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

	result :=  new(es2plus.ES2ProfileStatusResponse)
	functionCallIdentifier := "kadkjfad"
	iccid := "8965030119040000067"
	statusRequest := es2plus.NewStatusRequest(iccid, *requesterId, functionCallIdentifier)
	client := es2plus.NewClient(*certFilePath, *keyFilePath)
	err:= es2plus.MarshalUnmarshalGeneriEs2plusCommand(client,   *hostport, "getProfileStatus", statusRequest, result)
	if err != nil {
		panic(err)
	}

	fmt.Println("result -> ", result)
}
