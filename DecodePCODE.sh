#!/usr/bin/env bash

# connects to PeopleSoft database, and creates subdirectories in working dir with extracted PeopleCode and SQL text.

java -classpath ./bin:ojdbc14.jar:sqljdbc.jar  -Djava.util.logging.config.file=logger.properties decodepcode.Controller ProcessToFile $*