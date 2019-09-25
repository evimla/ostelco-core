package main

import (
	"bufio"
	"fmt"
	"gotest.tools/assert"
	"log"
	"os"
	"regexp"
	"testing"
)

type OutputFileRecord struct {
	Filename string
}

const (
	INITIAL            = "initial"
	HEADER_DESCRIPTION = "header_description"
	INPUT_VARIABLES    = "input_variables"
	OUTPUT_VARIABLES   = "output_variables"
)

type ParserState struct {
	CurrentState string
}

func ReadOutputFile(filename string) (OutputFileRecord, error) {

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
		CurrentState: INITIAL,
	}

	scanner := bufio.NewScanner(file)
	for scanner.Scan() {
		line := scanner.Text()
		if isComment(line) {
			log.Print("comment recognized")
			continue
		} else if isSectionHeader(line) {
			log.Print("Section header recognized")
			nextMode := modeFromSectionHeader(line)
			transitionMode(state, nextMode)
			continue
		}

		fmt.Println(line)

		// Then select parsing actions according to mode.
	}

	if err := scanner.Err(); err != nil {
		log.Fatal(err)
	}

	result := OutputFileRecord{
		Filename: filename,
	}

	return result, nil
}

func transitionMode(state ParserState, targetState string) {
	log.Printf("Transitioning from state '%s' to '%s'", state.CurrentState, targetState)
	state.CurrentState = targetState
}

func modeFromSectionHeader(s string) string {
	return INITIAL // TODO: Fix
}

func isSectionHeader(s string) bool {
	match, _ := regexp.MatchString("^\\*([A-Z0-9 ])+$", s)
	return match
}

func isComment(s string) bool {
	match, _ := regexp.MatchString("^\\*+$", s)
	return match
}

func Test(t *testing.T) {
	sample_output_file_name := "sample_out_file_for_testing.out"
	outputFileRecord, _ := ReadOutputFile(sample_output_file_name)

	assert.Equal(t, sample_output_file_name, outputFileRecord.Filename)
}
