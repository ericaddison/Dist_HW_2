# Distributed Systems HW 2
<UT Austin, Fall 2017> <Audrey Addison, Eric Addison>

## Description
The programming portion of this HW was to implement a fault-tolerant distributed ticket-booking system. The list of "seat assignments"
in this system acts as a shared resource, so read or write modifications to the list require access to a *critical section* of code.
Access to the critical section is managed by Lamport's mutex algorithm.

## Directory Structure
The source code does not use packages for modularity. The directory structure is:
- inputs: input config files for client and server
- lib: library directory containing `Jackson` JSON library for Java
- src: all source code for this HW

## Key Code
Most of the code handles processing messages or TCP connections. Here are some key notes about the code:
- The core of the Lamport Mutex algorithm is implemented in a set of methods near the top of the `LamportMutex` class.
- Fault tolerance is achieved by:
  - Having the client attempt to reconnect to a different server if the connection is lost
  - Updating the number of live servers in the Mutex algorithm
  - Allowing servers to reconnect after going down

We did not address the issue of unresponsive servers ... only dead connections.

## How to build
This code has one library dependency, which is a Java library for JSON serialization. This library must be included in the `classpath`. A script is included to compile the java code into a JAR file in `build.sh`, which runs the following commands:
```shell
mkdir build
javac -classpath ./lib/jackson-annotations-2.9.1.jar:./lib/jackson-databind-2.9.1.jar:./lib/jackson-core-2.9.1.jar:. -d ./build/ src/*.java

cd build
jar cvf ../HW2.jar *

cd ..
rm -rf build
```


## How to run
Scripts are provided to run the `Server` or `Client` class from the compiled JAR file. They are called by:
```shell
./run_client.sh inputs/client1.txt          # run Client.main() with the given config file

./run_server.sh inputs/server1.txt          # run Server.main() with the give config file

./run_server.sh inputs/server2.txt restart  # restart a failed server (server 2)
```

The Servers would like to be started in order, however this is not necessary. All servers must start initially before a client can reliably connect. After the initial startup, servers can fail and come back up (using the `restart` option when launching), as long as one server is still running.
