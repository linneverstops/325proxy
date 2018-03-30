import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Hashtable;

/**
 * TungHo Lin
 * txl429
 * EECS325
 * Project 1
 */

//multithreaded Web Proxy with DNS Cache
public class proxyd {

    private int port;

    private DNSCache dnsCache;

    private ServerSocket server;

    private proxyd(int port) {
        this.port = port;
        dnsCache = new DNSCache();
    }

    public static void main(String[] args) {
        //enforce correct input
        while(true) {
            if (!args[0].equals("-port") || args.length != 2) {
                System.err.println("Invalid input! Expected input: -port <portNo>");
            }
            else
                break;
        }
        int port = Integer.parseInt(args[1]);
        //create a new proxyd obj
        proxyd proxy = new proxyd(port);
        try {
            proxy.start();
        }
        catch (IOException ioe) {
            System.err.println("IOException: Failure on starting proxy");
        }
    }

    //start operation that initializes the proxyd and creates a thread for
    //each connection
    private void start() throws IOException {
        this.server = new ServerSocket(this.port);
        System.out.printf("Listening to Port %d\n", this.port);
        //create a ServerThread to handle each connection
        while (true) {
            Socket socket = server.accept();
            //create a new thread with a Runnable ServerThread
            Thread serverThread = new Thread(new ServerThread(socket, dnsCache));
            serverThread.start();
        }
    }

    //an inner class DNSCache that acts as a DNS Cache for this Web Proxy
    private class DNSCache {
        private final int expireTime = 30; //default expire time
        private Hashtable<String, String> caches; //a hashtable that stores all the caches
        private Hashtable<String, Long> timeCreated; //a hashtable that records the times the caches are inserted

        public DNSCache() {
            this.caches = new Hashtable<>();
            this.timeCreated = new Hashtable<>();
        }

        //synchronized to make sure only one thread execute this method only
        //and also to make sure the change is visible to all threads
        public synchronized String hostLookUp(String host) {
            //the input host can be null sometimes
            if(host == null)
                return null;
            final long now = System.nanoTime();
            //try to find if the host already exists in the caches
            Long hostTimeCreated = timeCreated.get(host);
            //if the host is cached before
            if (hostTimeCreated != null) {
                //if the time between now and the time when the host was created is less than 30 seconds
                if (((now - hostTimeCreated) / 1000000000) <= expireTime) {
                    System.out.println("DNS Cache HIT and active");
                    return caches.get(host);
                }
                //if the time difference is larger than 30 seconds
                System.out.println("DNS Cache EXPIRED, will request again");
            }
            //if the host is not cached before/needs to be recached
            try {
                String ip = InetAddress.getByName(host).getHostAddress();
                caches.put(host, ip);
                timeCreated.put(host, now);
                System.out.printf("DNS Cache CREATED: host=%s, ip=%s\n", host, ip);
                return ip;
            }
            catch (UnknownHostException uhe) {
                System.out.println("Unknown Host, cannot cache the host into DNS");
                return null;
            }
        }
    }

    //inner class ServerThread that handle one connection
    private class ServerThread implements Runnable {

        //set the max buffer size to be 64KB
        private final int max = 65536;

        private Socket serverSocket = null;

        private Socket clientSocket = null;

        private String method = null;

        // Store the Uniform Resource Identifier of the request
        private String url = null;

        private String version = null;

        private String host = null;

        ArrayList<String> headers = null;

        private byte[] body = null;

        private int contentLength = 0;

        private int port = 80;

        private DNSCache dnsCache = null;

        private String ip;

        ServerThread(Socket socket, DNSCache dnsCache) {
            this.dnsCache = dnsCache;
            this.clientSocket = socket;
            this.headers = new ArrayList<>();
        }

        public void run() {
            try {
                //Read HTTP request from client, parse through the message and save useful info to fields
                readRequestFromClient();
                //if the host is null
                //reasons leading to this is explained in README.txt
                if(this.host == null) {
                    System.err.println("Could not resolve host");
                    return;
                }
                //find the cache in the DNS Cache
                this.ip = dnsCache.hostLookUp(this.host);

                serverSocket = new Socket(ip, port);
                //Send the request to server
                sendRequestToServer();
                //Read data from server and send it to client
                readFromServerSendToClient();
                //Close server and client Sockets
                this.serverSocket.close();
                this.clientSocket.close();
            }
            catch (IOException ioe) {
                System.err.println(ioe.toString());
                ioe.printStackTrace();
            }
        }

        private void readRequestFromClient() throws IOException {
            InputStream inputStream = this.clientSocket.getInputStream();
            byte[] buffer = new byte[max];
            int n = inputStream.read(buffer);
            if (n == -1) {
                //simply return(close connection) if there is no request from client
                return;
            }
            int endOfHeader = 0; //Indicate the starting index of "the end of header lines"
            //Find the end of the header lines
            for (int i = 0; i < buffer.length; i++) {
                if (buffer[i] == '\r' && buffer[i + 1] == '\n' && buffer[i + 2] == '\r' && buffer[i + 3] == '\n') {
                    endOfHeader = i;
                    break;
                }
            }
            // Transfer read bytes to String for manipulation
            String header = new String(buffer, 0, endOfHeader);
            //save the position of the start of Entity Body(for later parse through the body(if any))
            int startOfEntity = endOfHeader + 4;
            //split the request line and header lines into individual line
            //lines[0] would be the request line
            //lines[1-(lines.length-1)] would be the header lines
            String[] lines = header.split("\\r\\n");
            //split the request line using space
            String[] requestLine = lines[0].split(" ");
            //the 3 elements of the request line are separated by a space
            this.method = requestLine[0];
            this.url = requestLine[1];
            this.version = requestLine[2];

            //parse the header lines, skip the first request line because it is processed already
            for (int i = 1; i < lines.length; i++) {
                //split each header line into header field name and value
                String[] splitHeader = lines[i].split(": ");
                if (splitHeader.length != 2)
                    continue;
                //handle blocking reads by forcing the server to not use persistent connection
                if (splitHeader[0].equalsIgnoreCase("Proxy-Connection") ||
                        splitHeader[0].equalsIgnoreCase("Connection"))
                    //do not add the Connection header line
                    continue;
                //if there is entity body, save it
                if (splitHeader[0].equalsIgnoreCase("Content-Length"))
                    this.contentLength = Integer.parseInt(splitHeader[1]);
                //if there is a host, save it
                if (splitHeader[0].equalsIgnoreCase("Host"))
                    this.host = splitHeader[1];
                //add the header lines back to a String array
                this.headers.add(lines[i]);
            }

            //add the "Connection: close" to the header lines at last
            this.headers.add("Connection: close");

            //change the http:// URL into absolute URL
            if (this.url.startsWith("http://")) {
                //check if there is another / after http://
                int p = this.url.indexOf('/', 7);
                //if yes, then there is a URL after the hostname
                if (p > 7) {
                    //set the host if the host name is still null
                    if (this.host == null) {
                        this.host = this.url.substring(7, p);
                        this.headers.add("Host: " + host);
                    }
                    this.url = this.url.substring(p);
                }
            }

            if (this.contentLength > 0) {
                //chop the "body" off the original buffer array
                this.body = Arrays.copyOfRange(buffer, startOfEntity, startOfEntity + this.contentLength);
            }

            //if the url contains a port no, take that port no and connect to it
            if (this.host != null) {
                String[] hostPort = this.host.split(":");
                this.host = hostPort[0]; //if there is no port no, the host would remain the same
                if (hostPort.length == 2)
                    this.port = Integer.parseInt(hostPort[1]);
            }
        }

        private void sendRequestToServer() throws IOException {
            OutputStream outputStream = this.serverSocket.getOutputStream();
            //writing the request line
            String requestMsg = this.method + " " + this.url + " " + this.version + "\r\n";

            //writing the header lines
            for (String header : this.headers) {
                requestMsg += (header + "\r\n");
            }

            //writing the blank line between header lines and entity body(if any)
            //flush each time write is called
            requestMsg += "\r\n"; //Indicate the end of header lines
            outputStream.write(requestMsg.getBytes("ASCII"));
            outputStream.flush();
            //write body if any
            if (this.body != null) {
                outputStream.write(this.body);
                outputStream.flush();
            }
            //print out the info about this connection
            System.out.printf("HTTP Request Msg to Server:\n");
            System.out.printf("Method: %s\n", this.method);
            System.out.printf("URL: %s\n", this.url);
            System.out.printf("Host: %s\n", this.host);
            System.out.printf("IP: %s\n", this.ip);
            System.out.printf("Port: %s\n", this.port);
            if (this.body != null)
                System.out.printf("Body: %s\n", new String(this.body));
            System.out.print("\n");
        }

        private void readFromServerSendToClient() throws IOException {
            InputStream inputStream = this.serverSocket.getInputStream();
            OutputStream outputStream = this.clientSocket.getOutputStream();
            byte[] b = new byte[max];
            //read until the end of the response msg
            int n;
            while ((n = inputStream.read(b)) > 0) {
                outputStream.write(b, 0, n);
                outputStream.flush();
            }
        }
    }

}
