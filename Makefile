VENV = venv

PYTHON = $(VENV)/bin/python3

PIP = $(VENV)/bin/pip3
REQUIREMENTS = scripts/requirements.txt
SCRIPT_DEPLOY = scripts/cf_deploy.py

######################################################
#
# Deploy targets
#
######################################################

CLEAN_CF ?= True
MT ?= False

ifeq ($(MT),$(filter $(MT),1 true True))
	MT_FLAG=--multi-tenant
endif

deploy-all: deploy-spring deploy-java deploy-node
.PHONY: deploy-all

deploy-spring: $(VENV)/bin/activate
	@ $(PYTHON) $(SCRIPT_DEPLOY) \
		--type java --path spring-security-ams --name spring-ams --cleanup $(CLEAN_CF)
.PHONY: deploy-spring

deploy-java: $(VENV)/bin/activate
	@ $(PYTHON) $(SCRIPT_DEPLOY) \
		--type java --path java-security-ams --name java-ams --cleanup $(CLEAN_CF) $(MT_FLAG)
.PHONY: deploy-java

deploy-node: $(VENV)/bin/activate
	@ $(PYTHON) $(SCRIPT_DEPLOY) \
		--type node --path nodejs-ams --name node-ams --cleanup $(CLEAN_CF) --package-path nodejs-ams/apps/node-webserver
.PHONY: deploy-node

######################################################
#
# Test targets
#
######################################################

test-all: $(VENV)/bin/activate
	@ (cd scripts && ../$(PYTHON) -m unittest execute_tests.py -v)
.PHONY: test-all

test-spring: $(VENV)/bin/activate
	@ (cd scripts && ../$(PYTHON) -m unittest execute_tests.TestAmsSpring -v)
.PHONY: test-spring

test-java: $(VENV)/bin/activate
	@ (cd scripts && ../$(PYTHON) -m unittest execute_tests.TestAmsJava -v)
.PHONY: test-java

test-node: $(VENV)/bin/activate
	@ (cd scripts && ../$(PYTHON) -m unittest execute_tests.TestAmsNode -v)
.PHONY: test-node

######################################################
#
# Help targets
#
######################################################

PYTHON_SET_VERSION := $(if $(PYTHON_SET_VERSION),$(PYTHON_SET_VERSION),python3)

$(VENV)/bin/activate: $(REQUIREMENTS)
	$(PYTHON_SET_VERSION) -m venv $(VENV)
	$(PIP) install wheel
	$(PIP) install -r $(REQUIREMENTS)

clean:
	rm -rf __pycache__
	rm -rf $(VENV)
.PHONY: clean

# creates venv dir and install python dependencies
install: $(VENV)/bin/activate
.PHONY: install

clean-cf: $(VENV)/bin/activate
	@ $(PYTHON) $(SCRIPT_DEPLOY) -n '' --cleanup True
.PHONY: clean-cf

# explanation https://earthly.dev/blog/python-makefile/
