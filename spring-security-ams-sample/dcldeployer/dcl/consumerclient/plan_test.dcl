// SPDX-FileCopyrightText: 2020
// SPDX-License-Identifier: Apache-2.0

TEST consumerClientTest {
	GRANT read ON system POLICY defaultPlan;
	GRANT write ON system POLICY defaultPlan;
	DENY read ON * POLICY defaultPlan;
}