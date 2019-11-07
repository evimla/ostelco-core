package store

import (
	"fmt"
	_ "github.com/mattn/go-sqlite3"
	"github.com/ostelco/ostelco-core/sim-administration/sim-batch-management/model"
	"gotest.tools/assert"
	"os"
	"reflect"
	"testing"
)

var sdb *SimBatchDB
var err error

func TestMain(m *testing.M) {
	setup()
	code := m.Run()
	shutdown()
	os.Exit(code)
}

func setup() {
	// sdb, err = store.OpenFileSqliteDatabase("bazunka.db")
	sdb, err = NewInMemoryDatabase()
	if err != nil {
		fmt.Errorf("Couldn't open new in memory database  '%s", err)
	}
	err = sdb.GenerateTables()
	if err != nil {
		fmt.Errorf("Couldn't generate tables  '%s'", err)
	}
}

func shutdown() {
	sdb.DropTables()
	if err != nil {
		fmt.Errorf("Couldn't drop tables  '%s'", err)
	}
}

// ... just to know that everything is sane.
func TestMemoryDbPing(t *testing.T) {
	err = sdb.Db.Ping()
	if err != nil {
		fmt.Errorf("Could not ping in-memory database. '%s'", err)
	}
}

func injectTestBatch() *model.Batch {
	theBatch := model.Batch{
		Name:            "SOME UNIQUE NAME",
		OrderDate:       "20200101",
		Customer:        "firstInputBatch",
		ProfileType:     "banana",
		BatchNo:         "100",
		Quantity:        100,
		Url:             "http://vg.no",
		FirstIccid:      "1234567890123456789",
		FirstImsi:       "123456789012345",
		MsisdnIncrement: -1,
	}

	err := sdb.CreateBatch(&theBatch)
	if err != nil {
		panic(err)
	}
	return &theBatch
}

func TestGetBatchById(t *testing.T) {

	theBatch := injectTestBatch()

	firstInputBatch, _ := sdb.GetBatchById(1)
	if !reflect.DeepEqual(*firstInputBatch, *theBatch) {
		fmt.Errorf("getBatchById failed")
	}
}

func TestGetAllBatches(t *testing.T) {

	theBatch := injectTestBatch()

	allBatches, err := sdb.GetAllBatches()
	if err != nil {
		fmt.Errorf("Reading query failed '%s'", err)
	}

	assert.Equal(t, len(allBatches), 1)

	firstInputBatch := allBatches[0]
	if !reflect.DeepEqual(firstInputBatch, theBatch) {
		fmt.Errorf("getBatchById failed")
	}
}

func declareTestBatch(t *testing.T) *model.Batch {

	theBatch, err := sdb.DeclareBatch(
		"Name",
		"Customer",
		"8778fsda",             // batch number
		"20200101",             // date string
		"89148000000745809013", // firstIccid string,
		"89148000000745809013", // lastIccid string,
		"242017100012213",      // firstIMSI string,
		"242017100012213",      // lastIMSI string,
		"47900184",             // firstMsisdn string,
		"47900184",             // lastMsisdn string,
		"BAR_FOOTEL_STD",       //profileType string,
		"1",                    // batchLengthString string,
		"LOL",                  // hssVendor string,
		"localhost",            // uploadHostname string,
		"8088",                 // uploadPortnumber string,
		"snuff",                // profileVendor string,
		"ACTIVE") // initialHlrActivationStatusOfProfiles string

	if err != nil {
		t.Fatal(err)
	}
	return theBatch
}

func TestDeclareBatch(t *testing.T) {
	theBatch := declareTestBatch(t)
	retrievedValue, _ := sdb.GetBatchById(theBatch.Id)
	if !reflect.DeepEqual(*retrievedValue, *theBatch) {
		t.Fatal("getBatchById failed")
	}
}

func entryEqual(a *model.SimEntry, b *model.SimEntry) bool {
	return ((a.BatchID == b.BatchID) && (a.RawIccid == b.RawIccid) && a.IccidWithChecksum == b.IccidWithChecksum	&& a.IccidWithoutChecksum == b.IccidWithoutChecksum && a.Iccid == b.Iccid && a.Imsi == b.Imsi && a.Msisdn == b.Msisdn && a.Ki == b.Ki)
}

func TestDeclareAndRetrieveSimEntries(t *testing.T) {
	theBatch := declareTestBatch(t)
	batchId := theBatch.Id

	entry := model.SimEntry{
		BatchID:              batchId,
		RawIccid:             "1",
		IccidWithChecksum:    "2",
		IccidWithoutChecksum: "3",
		Iccid:                "4",
		Imsi:                 "5",
		Msisdn:               "6",
		Ki:                   "7",
	}

	// assert.Equal(t, 0, entry.SimId)
	sdb.CreateSimEntry(&entry)
	assert.Assert(t, entry.SimId != 0)

	retrivedEntry, err := sdb.GetSimEntryById(entry.SimId)
	if err != nil {
		t.Fatal(err)
	}
	if !entryEqual(retrivedEntry, &entry) {
		t.Fatal("Retrieved and stored sim entry are different")
	}

	retrievedEntries, err := sdb.GetAllSimEntriesForBarch(entry.BatchID)
	if err != nil {
		t.Fatal(err)
	}
	assert.Equal(t, 1, len(retrievedEntries))

	retrivedEntry = &(retrievedEntries[0])

	if !entryEqual(retrivedEntry, &entry) {
		t.Fatal("Retrieved and stored sim entry are different")
	}
}
