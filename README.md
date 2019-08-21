Akka Persistence with Scala

### How to install
- either clone the repo or download as zip
- open with IntelliJ as an SBT project

No need to do anything else, as the IDE will take care to download and apply the appropriate library dependencies.

### Contents in this repo

* a filled-in `build.sbt` with the appropriate library dependencies
* a complete Scala IntelliJ project with a `Playground` app that you can compile to see that the libraries were downloaded
* a `docker-compose.yml` with already configured Docker containers for Postgres and Cassandra
* a simple `docker-clean.sh` script to remove your Docker containers if you want to start them fresh
* a `/sql` folder with a SQL script that will automatically be run in the Postgres container and create the correct tables for Akka (more on that in the PostgreSQL lecture)
* a helper script `psql.sh` to easily connect to Postgres once started
* a helper scripte `cqlsh.sh` to easily connect to Cassandra once started
