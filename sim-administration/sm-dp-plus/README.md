

# Testing the server using a client certificate



     curl -k --insecure -vvvv --request GET  --cert ../certificate-authority-simulated/crypto-artefacts/sm-dp-plus/ck.crt:superSecreet   --key ../certificate-authority-simulated/crypto-artef/sm-dp-plus/ck.key  "https://localhost:8443/ping"