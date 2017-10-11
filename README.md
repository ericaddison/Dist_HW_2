# Distributed Systems HW 2
<UT Austin, Fall 2017> <Audrey Addison, Eric Addison>

## Description
The programming portion of this HW was to implement a fault-tolerant distributed ticket-booking system. The list of "seat assignments"
in this system acts as a shared resource, so read or write modifications to the list require access to a *critical section* of code.
Access to the critical section is managed by Lamport's mutex algorithm.

## Key Code
Most of the code is dealing with processing messages or TCP connections. Here are some key notes about the code:
- The core of the Lamport Mutex algorithm is implemented in a set of methods near the top of the `LamportMutex` class.
- Fault tolerance is achieved by:
  - Having the client attempt to reconnect to a different server if the connection is lost
  - Updating the number of live servers in the Mutex algorithm
  - Allowing servers to reconnect after going down

We did not address the issue of unresponsive servers ... only dead connections.

## How to run
...
