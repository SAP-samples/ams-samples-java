// SPDX-FileCopyrightText: 2020
// SPDX-License-Identifier: Apache-2.0

POLICY readAllOwnItems {
    GRANT read ON * WHERE author.createdBy = $user.user_uuid AND $user.user_uuid IS NOT RESTRICTED;
}

POLICY readAllOwnItems_new {
    GRANT read ON * WHERE author.createdBy = $user.user_uuid OR author.updatedBy = $user.user_uuid;
}