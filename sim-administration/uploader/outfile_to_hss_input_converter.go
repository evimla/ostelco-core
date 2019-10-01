//usr/bin/env go run "$0" "$@"; exit "$?"
/**
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

package main

import (
	"bufio"
	"flag"
	"fmt"
	"log"
	"os"
	"regexp"
	"strconv"
	"strings"
)

///
///   Main.
///

func main() {
	inputFile, outputFilePrefix := parseCommandLine()

	fmt.Println("inputFile = ", inputFile)
	fmt.Println("outputFilePrefix = ", outputFilePrefix)

	outRecord := ReadOutputFile(inputFile)
	outputFile := outputFilePrefix + outRecord.outputFileName + ".csv"
	fmt.Println("outputFile = ", outputFile)

	err := WriteHssCsvFile(outputFile, outRecord.entries)
	if err != nil {
		log.Fatal("Couldn't close output file '", outputFilePrefix, "'.  Error = '", err, "'")
	}
}

///
/// Data structures
///

type OutputFileRecord struct {
	Filename          string
	inputVariables    map[string]string
	headerDescription map[string]string
	entries           []SimEntry
	noOfEntries       int
	outputFileName    string
}

const (
	INITIAL            = "initial"
	HEADER_DESCRIPTION = "header_description"
	INPUT_VARIABLES    = "input_variables"
	OUTPUT_VARIABLES   = "output_variables"
	UNKNOWN_HEADER     = "unknown"
)

type SimEntry struct {
	rawIccid             string
	iccidWithChecksum    string
	iccidWithoutChecksum string
	imsi                 string
	ki                   string
	outputFileName       string
}

type ParserState struct {
	currentState      string
	inputVariables    map[string]string
	headerDescription map[string]string
	entries           []SimEntry
}

///
///  Functions
///

func ParseLineIntoKeyValueMap(line string, theMap map[string]string) {
	var splitString = strings.Split(line, ":")
	if len(splitString) != 2 {
		log.Fatalf("Unparsable colon separated key/value pair: '%s'\n", line)
	}
	key := strings.TrimSpace(splitString[0])
	value := strings.TrimSpace(splitString[1])
	theMap[key] = value
}

func trimSuffix(s string, suffixLen int) string {
	return s[:len(s)-suffixLen]
}

func ReadOutputFile(filename string) OutputFileRecord {

	_, err := os.Stat(filename)

	if os.IsNotExist(err) {
		log.Fatalf("Couldn't find file '%s'\n", filename)
	}
	if err != nil {
		log.Fatalf("Couldn't stat file '%s'\n", filename)
	}

	file, err := os.Open(filename) // For read access.
	if err != nil {
		log.Fatal(err)
	}

	defer file.Close()

	state := ParserState{
		currentState:      INITIAL,
		inputVariables:    make(map[string]string),
		headerDescription: make(map[string]string),
	}

	scanner := bufio.NewScanner(file)
	for scanner.Scan() {

		// Read line, trim spaces in both ends.
		line := scanner.Text()
		line = strings.TrimSpace()

		// Is this a line we should read quickly then
		// move on to the next...?
		if isComment(line) {
			continue
		} else if isSectionHeader(line) {
			nextMode := modeFromSectionHeader(line)
			transitionMode(&state, nextMode)
			continue
		}

		// ... or should we look closer at it and parse it
		// looking for real content?

		switch (state.currentState) {
		case HEADER_DESCRIPTION:
			ParseLineIntoKeyValueMap(line, state.headerDescription)
		case INPUT_VARIABLES:
			if line == "var_In:" {
				continue
			}
			ParseLineIntoKeyValueMap(line, state.inputVariables)
		case OUTPUT_VARIABLES:
			if line == "var_Out: ICCID/IMSI/KI" {
				continue
			}

			// We won't handle all variations, only the most common one
			// if more fancy variations are necessary (with pin codes etc), then
			// we'll add them as needed.
			if strings.HasPrefix(line, "var_Out: ") {
				log.Fatalf("Unknown output format, only know how to handle ICCID/IMSI/KI, but was '%s'\n", line)
			}

			line = strings.TrimSpace(line)
			if line == "" {
				continue
			}
			rawIccid, imsi, ki := parseOutputLine(line)

			iccidWithChecksum := rawIccid
			if strings.HasSuffix(rawIccid, "F") {
				iccidWithChecksum = trimSuffix(rawIccid, 1)
			}

			var iccidWithoutChecksum = trimSuffix(iccidWithChecksum, 1)
			// TODO: Enable this!! checkICCIDSyntax(iccidWithChecksum)
			entry := SimEntry{
				rawIccid:             rawIccid,
				iccidWithChecksum:    iccidWithChecksum,
				iccidWithoutChecksum: iccidWithoutChecksum,
				imsi:                 imsi,
				ki:                   ki}
			state.entries = append(state.entries, entry)

		case UNKNOWN_HEADER:
			continue

		default
			log.Fatalf("Unknown parser state '%s'\n", state.currentState)
		}
	}

	if err := scanner.Err(); err != nil {
		log.Fatal(err)
	}

	countedNoOfEntries := len(state.entries)
	declaredNoOfEntities, err := strconv.Atoi(state.headerDescription["Quantity"])

	if err != nil {
		log.Fatal("Could not find  declared quantity of entities")
	}

	if countedNoOfEntries != declaredNoOfEntities {
		log.Fatalf("Declared no of entities = %d, counted nunber of entities = %d. Mismatch!",
			declaredNoOfEntities,
			countedNoOfEntries)
	}

	result := OutputFileRecord{
		Filename:          filename,
		inputVariables:    state.inputVariables,
		headerDescription: state.headerDescription,
		entries:           state.entries,
		noOfEntries:       declaredNoOfEntities,
		outputFileName:    getOutputFileName(state),
	}

	return result
}

func getOutputFileName(state ParserState) string {
	return "" + getCustomer(state) + "_" + getProfileType(state) + "_" + getBatchNo(state)
}

func getBatchNo(state ParserState) string {
	return state.headerDescription["Batch No"]
}

func getProfileType(state ParserState) string {
	return state.headerDescription["ProfileType"]
}

func getCustomer(state ParserState) string {
	// TODO: Maker safe, so that it fails reliably if Customer is not in map.
	//       also use constant, not magic string
	return state.headerDescription["Customer"]
}

func parseOutputLine(s string) (string, string, string) {
	parsedString := strings.Split(s, " ")
	return parsedString[0], parsedString[1], parsedString[2]
}

func transitionMode(state *ParserState, targetState string) {
	state.currentState = targetState
}


// TODO: Consider replacing this thing with a map lookup.
func modeFromSectionHeader(s string) string {
	sectionName := s[1:len(s)]
	switch (sectionName) {
	case "HEADER DESCRIPTION":
		return HEADER_DESCRIPTION
	case "INPUT VARIABLES"
		return INPUT_VARIABLES
	case "OUTPUT VARIABLES"
		return OUTPUT_VARIABLES
	default return UNKNOWN_HEADER
	}
}

func isSectionHeader(s string) bool {
	match, _ := regexp.MatchString("^\\*([A-Z0-9 ])+$", s)
	return match
}

func isComment(s string) bool {
	match, _ := regexp.MatchString("^\\*+$", s)
	return match
}

/// XXX Add a main function that
//   a) Reads the output file, then produces a HSS input file from that
//   b) Later, integrate with the prime input generator, and add a
//      database to keep track of the workflow.
//
//

//
// Set up command line parsing
//
func parseCommandLine() (string, string) {
	inputFile := flag.String("input-file",
		"not  a valid filename",
		"path to .out file used as input file")

	outputFile := flag.String("output-file-prefix",
		"not  a valid filename",
		"prefix to path to .csv file used as input file, filename will be autogenerated")

	//
	// Parse input according to spec above
	//
	flag.Parse()
	return *inputFile, *outputFile
}

// fileExists checks if a file exists and is not a directory before we
// try using it to prevent further errors.
func fileExists(filename string) bool {
	info, err := os.Stat(filename)
	if os.IsNotExist(err) {
		return false
	}
	return !info.IsDir()
}

// TODO: Consider rewriting using https://golang.org/pkg/encoding/csv/
func WriteHssCsvFile(filename string, entries []SimEntry) error {

	if fileExists(filename) {
		log.Fatal("Output file already exists. '", filename, "'.")
	}

	f, err := os.Create(filename)
	if err != nil {
		log.Fatal("Couldn't create hss csv file '", filename, "': ", err)
	}

	_, err = f.WriteString("ICCID, IMSI, KI\n")
	if err != nil {
		log.Fatal("Couldn't header to  hss csv file '", filename, "': ", err)
	}

	max := 0
	for i, entry := range entries {
		s := fmt.Sprintf("%s, %s, %s\n", entry.iccidWithChecksum, entry.imsi, entry.ki)
		_, err = f.WriteString(s)
		if err != nil {
			log.Fatal("Couldn't write to  hss csv file '", filename, "': ", err)
		}
		max = i + 1
	}
	fmt.Println("Successfully written ", max, " sim card records.")
	return f.Close()
}
