/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */

// Loaded only by App Attest registration (dynamic import in appAttest.ts), so the ~600 KB certificate
// stack is evaluated on the one-off registration path, never on a cold isolate serving anything else.
import "reflect-metadata"; // @peculiar/x509's DI container (tsyringe) needs the Reflect metadata API
export { X509Certificate } from "@peculiar/x509";
