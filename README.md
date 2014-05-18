Logscale Agent
==============

[![Build Status](https://travis-ci.org/logscale/logscale-agent.png?branch=master)](https://travis-ci.org/logscale/logscale-agent)

The Logscale Agent indexes and forwards events as specified by the agent
configuration URL that it is started with.

### To test:

    gradle test

### To build packages:

    gradle build

### To start:

    java com.logscale.agent.Main file:conf.yml


Configuration
-------------
Agents need a name, an endpoint and a key pair.  A valid config file looks like:

    name: agent01
    endpoint: wss://my-logscale-api.herokuapp.com/
    key: |
      -----BEGIN RSA PRIVATE KEY-----
      ...
      -----END RSA PRIVATE KEY-----

To generate a valid config file, issue:

    LOGSCALE_AGENT_NAME=agent01
    LOGSCALE_API_ENDPOINT=wss://api.logscale.net/
    ###
    openssl genrsa -out privkey.pem 4096 >/dev/null 2>&1 && \
    touch logscale-agent.yml && \
    chmod 600 logscale-agent.yml && \
    (printf "name: $LOGSCALE_AGENT_NAME\nendpoint: $LOGSCALE_API_ENDPOINT\nkey: |\n"; cat privkey.pem | sed 's/^/  /') > logscale-agent.yml && \
    printf "\n\n## wrote '$LOGSCALE_AGENT_NAME' config: logscale-agent.yml\n## public key follows\n\n" && \
    openssl rsa -pubout -in privkey.pem 2>/dev/null && \
    rm privkey.pem && \
    echo

The rest of the configuration is delivered by [the API]
(https://github.com/logscale/logscale-api#configureagent).

When not using the "Add Agent" wizard in the UI, you can generate the JSON for
seeding the agent config node by pasting the public key from the above command
(followed by ^D) into the following command:

    LOGSCALE_AGENT_NAME=agent01
    LOGSCALE_FIREBASE_ENDPOINT=https://logscale.firebaseio.com
    ###
    printf "\n\n## JSON for agent seed config at $LOGSCALE_FIREBASE_ENDPOINT/agents/$LOGSCALE_AGENT_NAME\n\n{\n  \"id\" : \"$(uuidgen)\",\n  \"pubkey\" : \"%q\"\n}\n" "$(cat)" | tr -d "\$'"
