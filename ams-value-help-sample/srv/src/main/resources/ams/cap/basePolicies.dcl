// ---------------------------------HEADER_START-----------------------------------------------
// Generated from a CAP model by the SAP AMS Plugin (@sap/ams) 3.3.0
// hash of generated content: 91ac650b4a5038a6506d8c89ed0d2865de87434924cafbba30a26bdd54d9b873
// ----------------------------------HEADER_END------------------------------------------------

POLICY "admin" {
	ASSIGN ROLE "admin";
}

POLICY "Reader" {
	ASSIGN ROLE "Reader" WHERE description IS NOT RESTRICTED AND genre IS NOT RESTRICTED;
}

POLICY ValueHelpReader {
	ASSIGN ROLE ValueHelpReader;
}