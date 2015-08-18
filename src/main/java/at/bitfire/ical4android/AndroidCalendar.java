/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */
package at.bitfire.ical4android;

import android.accounts.Account;
import android.annotation.SuppressLint;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.EntityIterator;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Attendees;
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Events;
import android.util.Log;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.LinkedList;
import java.util.List;

import lombok.Cleanup;
import lombok.Getter;

/**
 * Represents a locally stored calendar, containing Events.
 * Communicates with the Android Contacts Provider which uses an SQLite
 * database to store the contacts.
 */
public abstract class AndroidCalendar {
    private static final String TAG = "ical4android.Calendar";

    final protected Account account;
    final protected ContentProviderClient providerClient;
    final AndroidEventFactory eventFactory;

    @Getter final private long id;
    @Getter private boolean isSynced, isVisible;

    protected AndroidCalendar(Account account, ContentProviderClient providerClient, AndroidEventFactory eventFactory, long id) {
        this.account = account;
        this.providerClient = providerClient;
        this.eventFactory = eventFactory;
        this.id = id;
    }

	
	/* class methods, constructor */

    public static void init(Context context ){
        // set class loader for iCal4j ResourceLoader
        Thread.currentThread().setContextClassLoader(context.getClassLoader());
    }

	@SuppressLint("InlinedApi")
	public static Uri create(Account account, ContentResolver resolver, ContentValues info) throws CalendarStorageException {
		@Cleanup("release") ContentProviderClient client = resolver.acquireContentProviderClient(CalendarContract.AUTHORITY);
		if (client == null)
			throw new CalendarStorageException("No calendar provider found (calendar storage disabled?)");

        if (android.os.Build.VERSION.SDK_INT >= 15) {
            // these values are generated by ical4android
            info.put(Calendars.ALLOWED_AVAILABILITY, Events.AVAILABILITY_BUSY + "," + Events.AVAILABILITY_FREE + "," + Events.AVAILABILITY_TENTATIVE);
            info.put(Calendars.ALLOWED_ATTENDEE_TYPES, Attendees.TYPE_NONE + "," + Attendees.TYPE_OPTIONAL + "," + Attendees.TYPE_REQUIRED + "," + Attendees.TYPE_RESOURCE);
        }

		Log.i(TAG, "Creating local calendar: " + info.toString());
		try {
			return client.insert(syncAdapterURI(Calendars.CONTENT_URI, account), info);
		} catch (RemoteException e) {
			throw new CalendarStorageException("Couldn't create calendar", e);
		}
	}

    public static AndroidCalendar[] findAll(Account account, ContentProviderClient provider, AndroidCalendarFactory factory) throws CalendarStorageException {
        @Cleanup EntityIterator iterCalendars = null;
        try {
            iterCalendars = CalendarContract.CalendarEntity.newEntityIterator(
                    provider.query(syncAdapterURI(CalendarContract.CalendarEntity.CONTENT_URI, account), null, null, null, null)
            );
        } catch (RemoteException e) {
            throw new CalendarStorageException("Couldn't query calendars", e);
        }

        List<AndroidCalendar> calendars = new LinkedList<>();
        while (iterCalendars.hasNext()) {
            ContentValues values = iterCalendars.next().getEntityValues();

            AndroidCalendar calendar = factory.newInstance(account, provider, values.getAsLong(Calendars._ID));
            calendar.populate(values);
            calendars.add(calendar);
        }
        return calendars.toArray(factory.newArray(calendars.size()));
    }

    public void update(ContentValues info) throws CalendarStorageException {
        try {
            providerClient.update(syncAdapterURI(ContentUris.withAppendedId(Calendars.CONTENT_URI, id)), info, null, null);
        } catch (RemoteException e) {
            throw new CalendarStorageException("Couldn't update calendar", e);
        }
    }

    public void delete() throws CalendarStorageException {
        try {
            providerClient.delete(syncAdapterURI(ContentUris.withAppendedId(Calendars.CONTENT_URI, id)), null, null);
        } catch (RemoteException e) {
            throw new CalendarStorageException("Couldn't delete calendar", e);
        }
    }


    protected void populate(ContentValues info) {
        isSynced = info.getAsInteger(Calendars.SYNC_EVENTS) != 0;
        isVisible = info.getAsInteger(Calendars.VISIBLE) != 0;
    }

    protected AndroidEvent[] query(String where, String[] whereArgs) throws CalendarStorageException {
        where = (where == null ? "" : "(" + where + ") AND ") + Events.CALENDAR_ID + "=?";
        whereArgs = ArrayUtils.add(whereArgs, String.valueOf(id));

        @Cleanup Cursor cursor = null;
        try {
            cursor = providerClient.query(
                    syncAdapterURI(Events.CONTENT_URI),
                    new String[] { Events._ID },
                    where, whereArgs, null);
        } catch (RemoteException e) {
            throw new CalendarStorageException("Couldn't query calendar events", e);
        }

        List<AndroidEvent> events = new LinkedList<>();
        while (cursor != null && cursor.moveToNext()) {
            AndroidEvent event = eventFactory.newInstance(this, cursor.getLong(0));
            events.add(event);
        }
        return events.toArray(eventFactory.newArray(events.size()));
    }

    protected int delete(String where, String[] whereArgs) throws CalendarStorageException {
        where = (where == null ? "" : "(" + where + ") AND ") + Events.CALENDAR_ID + "=?";
        whereArgs = ArrayUtils.add(whereArgs, String.valueOf(id));

        try {
            return providerClient.delete(syncAdapterURI(Events.CONTENT_URI), where, whereArgs);
        } catch (RemoteException e) {
            throw new CalendarStorageException("Couldn't delete calendar events", e);
        }
    }


    public static Uri syncAdapterURI(Uri uri, Account account) {
        return uri.buildUpon()
                .appendQueryParameter(Calendars.ACCOUNT_NAME, account.name)
                .appendQueryParameter(Calendars.ACCOUNT_TYPE, account.type)
                .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                .build();
    }

    public Uri syncAdapterURI(Uri uri) {
        return uri.buildUpon()
                .appendQueryParameter(Calendars.ACCOUNT_NAME, account.name)
                .appendQueryParameter(Calendars.ACCOUNT_TYPE, account.type)
                .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                .build();
    }

}
