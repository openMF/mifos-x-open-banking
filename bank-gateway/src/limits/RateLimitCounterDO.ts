/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */

import { DurableObject } from "cloudflare:workers";
import type { Env } from "../env";

/** Fixed-window counter, one object per rate-limit key; the alarm drops the window when it ends. */
export class RateLimitCounterDO extends DurableObject<Env> {
  /** Counts one request; true while the window's count is within `limit`. */
  async hit(limit: number, periodSec: number, nowMs: number): Promise<boolean> {
    const s = this.ctx.storage;
    let w = await s.get<{ start: number; count: number }>("window");
    if (!w || nowMs >= w.start + periodSec * 1000) {
      w = { start: nowMs, count: 0 };
      await s.setAlarm(nowMs + periodSec * 1000);
    }
    w.count++;
    await s.put("window", w);
    return w.count <= limit;
  }

  async alarm(): Promise<void> {
    await this.ctx.storage.deleteAll();
  }
}
