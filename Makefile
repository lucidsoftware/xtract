SCALA_NAME = scala-2.12
SCALA_VERSION = 2.12.9

.PHONY: all pages
all: pages package

.PHONY: clean-pages
clean-pages:
	rm -rf pages/core
	rm -rf pages/testing

pages: doc
	mkdir -p pages/core/api
	cp -rf xtract-core/target/$(SCALA_NAME)/api/* pages/core/api
	mkdir -p pages/testing/api
	cp -rf testing/target/$(SCALA_NAME)/api/* pages/testing/api

doc:
	sbt ++$(SCALA_VERSION) doc


.PHONY: %
%:
	sbt $*
