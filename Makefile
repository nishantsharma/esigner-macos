# eSigner macOS host.
#
#   make            list targets
#   make install    build and register with your browsers
#   make status     check an existing installation
#
# Compiling is a real dependency graph, so it lives in rules here. Deploying is
# not — it writes manifests into paths containing spaces, which make cannot even
# name as targets — so that part stays in scripts/.

SHELL := /bin/bash

DEST     := $(HOME)/Library/Application Support/eProcSigner
SOURCES  := $(shell find src -name '*.java' 2>/dev/null)
STAMP    := lib/.bootstrap-ok
JAR      := dist/esigner-mac.jar

# Chrome hands the host almost no PATH, so everything is resolved absolutely.
JAVA := $(shell for c in "$$ESIGNER_JAVA" /opt/homebrew/opt/openjdk/bin/java \
                         /usr/local/opt/openjdk/bin/java "$$(command -v java)"; do \
                    [ -n "$$c" ] && [ -x "$$c" ] && echo "$$c" && break; done)
JAVAC = $(dir $(JAVA))javac

# The macOS release this was developed and verified against. A mismatch is a
# warning, never a failure — see README.
TESTED_MACOS := 26
THIS_MACOS   := $(shell sw_vers -productVersion | cut -d. -f1)

.PHONY: help install uninstall reinstall status test test-token diagnose deps verify-deps clean distclean check-java check-macos

help:
	@echo 'eSigner macOS host'
	@echo
	@grep -E '^[a-z-]+:.*## ' $(MAKEFILE_LIST) \
	    | sed -e 's/:.*## /|/' \
	    | awk -F'|' '{printf "  \033[1m%-12s\033[0m %s\n", $$1, $$2}'
	@echo
	@echo "  java:   $(if $(JAVA),$(JAVA),NOT FOUND - brew install openjdk)"
	@echo "  macOS:  $$(sw_vers -productVersion) (developed against $(TESTED_MACOS).x)"

# ----------------------------------------------------------------- checks ----
check-java:
	@[ -n "$(JAVA)" ] || { \
	    echo "ERROR: no Java runtime found. Try: brew install openjdk" >&2; exit 1; }
	@[ -x "$(JAVAC)" ] || { \
	    echo "ERROR: $(JAVA) has no javac beside it - install a JDK, not a JRE" >&2; exit 1; }

check-macos:
	@if [ "$(THIS_MACOS)" != "$(TESTED_MACOS)" ]; then \
	    echo "NOTE: verified on macOS $(TESTED_MACOS).x; you are on $$(sw_vers -productVersion)."; \
	    echo "      Expected to work, but untested. 'make test' will tell you."; \
	fi

# ------------------------------------------------------------------ build ----
$(STAMP):
	@./bootstrap.sh
	@mkdir -p lib && touch $@

deps: $(STAMP) ## Fetch eSigner.jar and the PKCS#11 module from vendor packages

verify-deps: ## Check the vendor binaries' code signature and hashes
	@scripts/verify-deps.sh

$(JAR): $(SOURCES) $(STAMP) | check-java
	@echo "  compile  $(words $(SOURCES)) sources"
	@rm -rf build && mkdir -p build dist
	@"$(JAVAC)" -nowarn -cp lib/eSigner.jar -d build $(SOURCES)
	@"$(dir $(JAVA))jar" --create --file $@ -C build .
	@echo "  jar      $@"

build: $(JAR) ## Compile the SunMSCAPI stand-in

# ---------------------------------------------------------------- install ----
install: check-macos $(JAR) ## Install and register with every browser found
	@scripts/verify-deps.sh || { \
	    echo "Refusing to install an unverified PKCS#11 module." >&2; exit 1; }
	@echo
	@ESIGNER_JAVA="$(JAVA)" scripts/install-host.sh

uninstall: ## Remove the host and all browser manifests
	@scripts/uninstall-host.sh

reinstall: uninstall install ## Uninstall then install, for a clean slate

status: ## Check an existing installation and report versions
	@scripts/status.sh

# ------------------------------------------------------------------- test ----
test: $(JAR) ## Full signing test against a throwaway software token (no DSC)
	@test/run-smoke-test.sh

test-token: $(JAR) ## Full signing test against the real DSC - prompts for your PIN
	@test/run-token-test.sh

diagnose: $(JAR) ## Report every certificate on the token and try signing with each
	@test/run-cert-diagnostic.sh

# ------------------------------------------------------------------ clean ----
clean: ## Remove build output
	@rm -rf build dist test/*.class test/tokens test/softhsm2.conf
	@echo "  cleaned build output"

distclean: clean ## Also remove the fetched vendor binaries
	@rm -rf lib
	@echo "  removed lib/ - run 'make deps' to fetch again"
