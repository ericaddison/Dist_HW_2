file=`pwd`/$1
cd bin
java -classpath ../lib/jackson-annotations-2.9.1.jar:../lib/jackson-databind-2.9.1.jar:../lib/jackson-core-2.9.1.jar:. Client $1 $2
