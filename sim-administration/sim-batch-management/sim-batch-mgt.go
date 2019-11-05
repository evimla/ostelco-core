//usr/bin/env go run "$0" "$@"; exit "$?"
package main

import (
	"bufio"
	"fmt"
	"github.com/ostelco/ostelco-core/sim-administration/sim-batch-management/es2plus"
	"github.com/ostelco/ostelco-core/sim-administration/sim-batch-management/outfileconversion"
	"github.com/ostelco/ostelco-core/sim-administration/sim-batch-management/store"
	"github.com/ostelco/ostelco-core/sim-administration/sim-batch-management/uploadtoprime"
	kingpin "gopkg.in/alecthomas/kingpin.v2"
	"log"
	"os"
	"sync"
)

//  "gopkg.in/alecthomas/kingpin.v2"
var (
	// TODO: Enable, but also make it have an effect.
	// debug    = kingpin.Flag("debug", "enable debug mode").Default("false").Bool()

	//
	// Smoketest of the ES2Plus interface
	//
	smoketest             = kingpin.Command("es2plus-smoketest", "Smoketest the ES2+ protocol.")
	smoketestCertFilePath = smoketest.Flag("cert", "Certificate pem file.").Required().String()
	smoketestKeyFilePath  = smoketest.Flag("key", "Certificate key file.").Required().String()
	smoketestHostport     = smoketest.Flag("hostport", "host:port of ES2+ endpoint.").Required().String()
	smoketestRequesterId  = smoketest.Flag("requesterid", "ES2+ requester ID.").Required().String()
	smoketestIccidInput   = smoketest.Flag("iccid", "Iccid of profile to manipulate").Required().String()

	es2             = kingpin.Command("es2", "Do things with the ES2+ protocol")
	es2cmd          = es2.Arg("cmd", "The ES2+ subcommand, one of get-status, recover-profile, download-order, confirm-order, cancel-profile, bulk-activate-iccids, activate-iccid ").Required().String()
	es2iccid        = es2.Arg("iccid", "Iccid of profile to manipulate").Required().String()
	es2Target       = es2.Arg("target-state", "Target state of recover-profile or cancel-profile command").Default("AVAILABLE").String()
	es2CertFilePath = es2.Flag("cert", "Certificate pem file.").Required().String()
	es2KeyFilePath  = es2.Flag("key", "Certificate key file.").Required().String()
	es2Hostport     = es2.Flag("hostport", "host:port of ES2+ endpoint.").Required().String()
	es2RequesterId  = es2.Flag("requesterid", "ES2+ requester ID.").Required().String()

	//
	// Convert an output (.out) file from an sim profile producer into an input file
	// for Prime.
	//

	/**
	 * OLD COMMENTS: Not yet reworked into doc for this script, but mostly accurate
	 *  nonetheless.
	 *
	 * This program is intended to be used from the command line, and will convert an
	 * output file from a sim card vendor into an input file for a HSS. The assumptions
	 * necessary for this to work are:
	 *
	 *  * The SIM card vendor produces output files similar to the example .out file
	 *     found in the same source directory as this program
	 *
	 *  * The HSS accepts input as a CSV file, with header line 'ICCID, IMSI, KI' and subsequent
	 *    lines containing ICCID/IMSI/Ki fields, all separated by commas.
	 *
	 * Needless to say, the outmost care should be taken when handling Ki values and
	 * this program must, as a matter of course, be considered a security risk, as
	 * must all  software that touch SIM values.
	 *
	 * With that caveat in place, the usage of this program typically looks like
	 * this:
	 *
	 *    ./outfile_to_hss_input_converter.go  \
	 *              -input-file sample_out_file_for_testing.out
	 *              -output-file-prefix  ./hss-input-for-
	 *
	 * (followed by cryptographically strong erasure of the .out file,
	 *  encapsulation of the .csv file in strong cryptography etc., none
	 *  of which are handled by this script).
	 */

	spUpload                 = kingpin.Command("sim-profile-upload", "Convert an output (.out) file from an sim profile producer into an input file for an HSS.")
	spUploadInputFile        = spUpload.Flag("input-file", "path to .out file used as input file").Required().String()
	spUploadOutputFilePrefix = spUpload.Flag("output-file-prefix",
		"prefix to path to .csv file used as input file, filename will be autogenerated").Required().String()

	// TODO: Check if this can be used for the key files.
	// postImage   = post.Flag("image", "image to post").ExistingFile()

	//
	// Upload of batch data to Prime
	//
	pbu           = kingpin.Command("prime-batch-upload", "Generate a shellscript that will upload a batch of profiles to Prime.")
	pbuFirstIccid = pbu.Flag("first-rawIccid",
		"An 18 or 19 digit long string.  The 19-th digit being a luhn luhnChecksum digit, if present").Required().String()
	pbuLastIccid = pbu.Flag("last-rawIccid",
		"An 18 or 19 digit long string.  The 19-th digit being a luhn luhnChecksum digit, if present").Required().String()
	pbuFirstIMSI         = pbu.Flag("first-imsi", "First IMSI in batch").Required().String()
	pbuLastIMSI          = pbu.Flag("last-imsi", "Last IMSI in batch").Required().String()
	pbuFirstMsisdn       = pbu.Flag("first-msisdn", "First MSISDN in batch").Required().String()
	pbuLastMsisdn        = pbu.Flag("last-msisdn", "Last MSISDN in batch").Required().String()
	pbuProfileType       = pbu.Flag("profile-type", "SIM profile type").Required().String()
	pbuBatchLengthString = pbu.Flag(
		"batch-Quantity",
		"Number of sim cards in batch").Required().String()

	// XXX Legal values are Loltel and M1 at this time, how to configure that
	//     flexibly?  Eventually by putting them in a database and consulting it during
	//     command execution, but for now, just by squinting.

	pbuHssVendor                            = pbu.Flag("hss-vendor", "The HSS vendor").Default("M1").String()
	pbuUploadHostname                       = pbu.Flag("upload-hostname", "host to upload batch to").Default("localhost").String()
	pbuUploadPortnumber                     = pbu.Flag("upload-portnumber", "port to upload to").Default("8080").String()
	pbuProfileVendor                        = pbu.Flag("profile-vendor", "Vendor of SIM profiles").Default("Idemia").String()
	pbuInitialHlrActivationStatusOfProfiles = pbu.Flag(
		"initial-hlr-activation-status-of-profiles",
		"Initial hss activation state.  Legal values are ACTIVATED and NOT_ACTIVATED.").Default("ACTIVATED").String()

	// TODO ???
	batch = kingpin.Command("batch", "Utility for persisting and manipulating sim card batches.")

	//
	//    Declare a new batch
	//
	db           = kingpin.Command("declare-batch", "Declare a batch to be persisted, and used by other commands")
	dbName       = db.Flag("name", "Unique name of this batch").Required().String()
	dbCustomer   = db.Flag("customer", "Name of the customer of this batch (with respect to the sim profile vendor)").Required().String()
	dbBatchNo    = db.Flag("batch-no", "Unique number of this batch (with respect to the profile vendor)").Required().String()
	dbOrderDate  = db.Flag("order-date", "Order date in format ddmmyyyy").Required().String()
	dbFirstIccid = db.Flag("first-rawIccid",
		"An 18 or 19 digit long string.  The 19-th digit being a luhn luhnChecksum digit, if present").Required().String()
	dbLastIccid = db.Flag("last-rawIccid",
		"An 18 or 19 digit long string.  The 19-th digit being a luhn luhnChecksum digit, if present").Required().String()
	dbFirstIMSI         = db.Flag("first-imsi", "First IMSI in batch").Required().String()
	dbLastIMSI          = db.Flag("last-imsi", "Last IMSI in batch").Required().String()
	dbFirstMsisdn       = db.Flag("first-msisdn", "First MSISDN in batch").Required().String()
	dbLastMsisdn        = db.Flag("last-msisdn", "Last MSISDN in batch").Required().String()
	dbProfileType       = db.Flag("profile-type", "SIM profile type").Required().String()
	dbBatchLengthString = db.Flag(
		"batch-quantity",
		"Number of sim cards in batch").Required().String()

	dbHssVendor        = db.Flag("hss-vendor", "The HSS vendor").Default("M1").String()
	dbUploadHostname   = db.Flag("upload-hostname", "host to upload batch to").Default("localhost").String()
	dbUploadPortnumber = db.Flag("upload-portnumber", "port to upload to").Default("8080").String()

	dbProfileVendor = db.Flag("profile-vendor", "Vendor of SIM profiles").Default("Idemia").String()

	dbInitialHlrActivationStatusOfProfiles = db.Flag(
		"initial-hlr-activation-status-of-profiles",
		"Initial hss activation state.  Legal values are ACTIVATED and NOT_ACTIVATED.").Default("ACTIVATED").String()
)

func main() {

	db, err := store.OpenFileSqliteDatabase("foobar.db")
	if err != nil {
		panic(fmt.Sprintf("Couldn't open sqlite database.  '%s'", err))
	}

	db.GenerateTables()

	cmd := kingpin.Parse()
	switch cmd {
	case "es2plus-smoketest":
		es2PlusSmoketest(smoketestCertFilePath, smoketestKeyFilePath, smoketestHostport, smoketestRequesterId, smoketestIccidInput)
	case "sim-profile-upload":
		outfileconversion.ConvertInputfileToOutputfile(*spUploadInputFile, *spUploadOutputFilePrefix)
	case "declare-batch":
		fmt.Println("Declare batch")
		db.DeclareBatch(
			*dbName,
			*dbCustomer,
			*dbBatchNo,
			*dbOrderDate,
			*dbFirstIccid,
			*dbLastIccid,
			*dbFirstIMSI,
			*dbLastIMSI,
			*dbFirstMsisdn,
			*dbLastMsisdn,
			*dbProfileType,
			*dbBatchLengthString,
			*dbHssVendor,
			*dbUploadHostname,
			*dbUploadPortnumber,
			*dbProfileVendor,
			*dbInitialHlrActivationStatusOfProfiles)
	case "prime-batch-upload":
		// TODO: Combine these two into something inside uploadtoprime.
		//       It's unecessary to break the batch thingy open in this way.
		var batch = uploadtoprime.OutputBatchFromCommandLineParameters(
			pbuFirstIccid,
			pbuLastIccid,
			pbuLastIMSI,
			pbuFirstIMSI,
			pbuLastMsisdn,
			pbuFirstMsisdn,
			pbuUploadHostname,
			pbuUploadPortnumber,
			pbuHssVendor,
			pbuInitialHlrActivationStatusOfProfiles,
			pbuProfileType,
			pbuProfileVendor,
			pbuBatchLengthString)
		var csvPayload = uploadtoprime.GenerateCsvPayload(batch)
		uploadtoprime.GeneratePostingCurlscript(batch.Url, csvPayload)

	case "es2":

		// TODO: Vet all the parameters, they can  very easily be bogus.
		client := es2plus.Client(*es2CertFilePath, *es2KeyFilePath, *es2Hostport, *es2RequesterId)
		iccid := *es2iccid
		switch *es2cmd {
		case "get-status":
			result, err := es2plus.GetStatus(client, iccid)
			if err != nil {
				panic(err)
			}

			fmt.Printf("iccid='%s', state='%s', acToken='%s'\n", iccid, (*result).State, (*result).ACToken)
		case "recover-profile":
			checkEs2TargetState(es2Target)
			result, err := es2plus.RecoverProfile(client, iccid, *es2Target)
			if err != nil {
				panic(err)
			}
			fmt.Println("result -> ", result)
		case "download-order":
			result, err := es2plus.DownloadOrder(client, iccid)
			if err != nil {
				panic(err)
			}
			fmt.Println("result -> ", result)
		case "confirm-order":
			result, err := es2plus.ConfirmOrder(client, iccid)
			if err != nil {
				panic(err)
			}
			fmt.Println("result -> ", result)
		case "activate-iccid":
			result, err := es2plus.ActivateIccid(client, iccid)

			if err != nil {
				panic(err)
			}
			fmt.Printf("%s, %s\n", iccid, result.ACToken)

		case "bulk-activate-iccids":
			fmt.Printf("Ready to bulk activate some iccids\n")

			file, err := os.Open(iccid)
			if err != nil {
				log.Fatal(err)
			}
			defer file.Close()

			scanner := bufio.NewScanner(file)
			var mutex = &sync.Mutex{}
			var waitgroup sync.WaitGroup
			for scanner.Scan() {
				iccid := scanner.Text()
				waitgroup.Add(1)
				go func(i string) {
					mutex.Lock()
					fmt.Println("Iccid = ", i)
					mutex.Unlock()
					waitgroup.Done()
				}(iccid)
			}

			waitgroup.Wait()

			if err := scanner.Err(); err != nil {
				log.Fatal(err)
			}
			fmt.Println("Done")

		case "cancel-profile":
			checkEs2TargetState(es2Target)
			_, err := es2plus.CancelOrder(client, iccid, *es2Target)
			if err != nil {
				panic(err)
			}
		default:
			panic(fmt.Sprintf("Unknown es2+ subcommand '%s', try --help", *es2cmd))
		}
	case "batch":
		fmt.Println("Doing the batch thing.")
		// storage.doTheBatchThing()
	default:
		panic(fmt.Sprintf("Unknown command: '%s'\n", cmd))
	}
}

func checkEs2TargetState(target *string) {
	if *target != "AVAILABLE" {
		panic("Target ES2+ state unexpected, legal value(s) is(are): 'AVAILABLE'")
	}
}

// TODO:  This is just smoketest-code, delete it after
//        the smoketest-script has been rewritten a bit to do the same thing.
func es2PlusSmoketest(certFilePath *string, keyFilePath *string, hostport *string, requesterId *string, iccidInput *string) {

	fmt.Printf("certFilePath = '%s'\n", *certFilePath)
	fmt.Printf("keyFilePath  = '%s'\n", *keyFilePath)
	fmt.Printf("hostport     = '%s'\n", *hostport)
	fmt.Printf("requesterId  = '%s'\n", *requesterId)
	fmt.Printf("iccidInput   = '%s'\n", *iccidInput)

	iccid := *iccidInput

	client := es2plus.Client(*certFilePath, *keyFilePath, *hostport, *requesterId)

	result, err := es2plus.GetStatus(client, iccid)
	if err != nil {
		panic(err)
	}
	fmt.Println("result1 -> ", result.State)

	result2, err := es2plus.RecoverProfile(client, iccid, "AVAILABLE")
	if err != nil {
		panic(err)
	}

	fmt.Println("result2 -> ", result2)

	result, err = es2plus.GetStatus(client, iccid)
	if err != nil {
		panic(err)
	}

	fmt.Println("result3 -> ", result.State)

	// XXX Should be CancelProfile
	result4, err := es2plus.RecoverProfile(client, iccid, "AVAILABLE")
	if err != nil {
		panic(err)
	}

	fmt.Println("result4 -> ", result4)

	result, err = es2plus.GetStatus(client, iccid)
	if err != nil {
		panic(err)
	}

	fmt.Println("result5 -> ", result.State)

	if result.State != "AVAILABLE" {
		panic("Couldn't convert state of iccid into AVAILABLE")
	}

	result6, err := es2plus.DownloadOrder(client, iccid)
	fmt.Println("result6 -> ", result6)
	if err != nil {
		panic(err)
	}

	result7, err := es2plus.GetStatus(client, iccid)
	if err != nil {
		panic(err)
	}
	fmt.Println("result7 -> ", result7)

	result8, err := es2plus.ConfirmOrder(client, iccid)
	fmt.Println("result8 -> ", result8)
	if err != nil {
		panic(err)
	}

	result9, err := es2plus.GetStatus(client, iccid)
	if err != nil {
		panic(err)
	}
	fmt.Println("result9 -> ", result9)

	if result.State != "RELEASED" {
		panic("Couldn't convert state of sim profile into RELEASED")
	}

	fmt.Println("Success")
}
