//usr/bin/env go run "$0" "$@"; exit "$?"
package main

import (
	"flag"
	"fmt"
	"github.com/ostelco/ostelco-core/sim-administration/sim-batch-management/es2plus"
)

///
///   Main.  The rest should be put into a library.
///

func main() {

	certFilePath := flag.String("cert", "", "Certificate pem file.")
	keyFilePath := flag.String("key", "", "Certificate key file.")
	hostport := flag.String("hostport", "", "host:port of ES2+ endpoint.")
	requesterId := flag.String("requesterid", "", "ES2+ requester ID.")

	fmt.Printf("certFilePath = '%s'\n", *certFilePath)
	fmt.Printf("keyFilePath  = '%s'\n", *keyFilePath)
	fmt.Printf("hostport     = '%s'\n", *hostport)
	fmt.Printf("requesterId  = '%s'\n", *requesterId)

	flag.Parse()

	// TODO: Move the actual ICCID into the caller function
	iccid := "8965030119040000067"
	client := es2plus.Client(*certFilePath, *keyFilePath, *hostport, *requesterId)

	result, err := es2plus.GetStatus(client, iccid)
	if err != nil {
		panic(err)
	}

	// TODO:  Assert that the state is "AVAILABLE"

	fmt.Println("result -> ", result)

	/**
	// TODO:   Generate a full roundtrip taking some suitable profile through a proper
	//         activation, and reset.
	result, err := es2plus.Activate(client, iccid)
	if err != nil {
		panic(err)
	}

	result, err := es2plus.GetStatus(client, iccid)
	if err != nil {
		panic(err)
	}

	// Make some assertion about the status at this point
	result, err := es2plus.Reset(client, iccid)
	if err != nil {
		panic(err)
	}

	result, err := es2plus.GetStatus(client, iccid)
	if err != nil {
		panic(err)
	}

	// Make some assertion about the status at this point
*/
}
