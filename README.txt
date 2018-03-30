TungHo Lin
txl429
EECS325 
Project 1

README:
a)
Proxy port I am using: 5020

b)
To operate the proxy:
-compile the proxyd.java file by entering command:
"javac proxyd.java"

-execute the proxyd by entering command:
"java proxyd -port 5020"

-open up the browser proxy settings, set the proxy address and port no
and run non-https websites.

c)
I tested my proxy with Google Chrome. To test it locally,
I opened up Settings>Advance>open Proxy settings>LAN settings>
>check the box"use a proxy server for your LAN
>put 127.0.0.1 as the address and 5020 as the port no
>leave the "bypass proxy server for local addresses" unchecked

I have also tested my proxy on eecslinab1. It works fine.
d)
I tested my proxy on:
case.edu
cluster41.case.edu

-webpage successfully loaded
-proxy can handle multiple client requests
-text and binary data loaded
-DNS Cache works
-POST method works

e)
Weird Behaviors:
-Because I am using Google Chrome, when I load up the browser, some applications of Chrome required a connection
to google host; and because Google is a https website, it will not work on my web proxy. Therefore,
each time the web proxy comes across a https website request from the client, it will fail to connect
to google.com. Since the host field in ServerThread is initialized as null, host will remain as null
if the host can not be resolved by the web proxy. And after reading request from client, I call the DNS
host lookup method to get the IP address of the host. If host is null, the IP address will return the 
loop address to the local machine which is 127.0.0.1 
-If the proxy try to connect to itself on 127.0.0.1, it will throw an IO Exception: Connection Exception
connection refused. That's why I check if the host is null after receiving and parsing the Request Message
from client.
-Since a bunch of ServerThreads are trying to connect to a bunch of the google applications in the Goolgle Chrome,
these threads keep trying to connect to google host but failed when they reach a timeout. Therefore, sometime into
starting the web proxy, there will be multiple error outputs saying "Could not resolve host". 






















