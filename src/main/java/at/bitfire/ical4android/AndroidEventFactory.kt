/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.ical4android

import android.content.ContentValues

interface AndroidEventFactory<out T: AndroidEvent> {

    fun newInstance(calendar: AndroidCalendar, id: Long, baseInfo: ContentValues? = null): T
    fun newInstance(calendar: AndroidCalendar, event: Event): T

}