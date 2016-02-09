SCALA_NAME = scala-2.11

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


.PHONY: %
%:
	sbt $*
