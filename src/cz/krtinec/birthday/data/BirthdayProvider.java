/*
 * This file is part of Birthday Widget.
 *
 * Birthday Widget is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Birthday Widget is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Birthday Widget.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Copyright (c) Lukas Marek, 2011.
 */

package cz.krtinec.birthday.data;

import java.io.InputStream;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.content.*;
import android.os.RemoteException;
import cz.krtinec.birthday.dto.*;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.util.Log;

public class BirthdayProvider {    
	
	private static BirthdayProvider instance = new BirthdayProvider();
	private List<DatePattern> PATTERNS = new ArrayList<DatePattern>();
	private static String TIMESTAMP_PATTERN = "[-]{0,1}\\d{9,}";
    private static String[] BIRTHDAY_PROJECTION = new String[] { ContactsContract.Contacts.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Event.CONTACT_ID,
            ContactsContract.CommonDataKinds.Event.START_DATE,
            ContactsContract.Contacts.LOOKUP_KEY,
            ContactsContract.Contacts.PHOTO_ID,
            ContactsContract.CommonDataKinds.Event.TYPE,
            ContactsContract.CommonDataKinds.Event.LABEL
        };

	
	private BirthdayProvider() {
		PATTERNS.add(new DatePattern("\\d{4}\\-\\d{1,2}\\-\\d{1,2}", "yyyy-MM-dd", DateIntegrity.FULL));
		PATTERNS.add(new DatePattern("\\d{2}\\-\\d{1,2}\\-\\d{1,2}", "yy-MM-dd", DateIntegrity.FULL));
		PATTERNS.add(new DatePattern("\\-\\-\\d{1,2}\\-\\d{1,2}", "--MM-dd", DateIntegrity.WITHOUT_YEAR));
		PATTERNS.add(new DatePattern("\\d{8}", "yyyyMMdd", DateIntegrity.FULL));
	}
	
	public static BirthdayProvider getInstance() {
		return instance;
	}

    public List<Event> getEvents(Uri contact, Context ctx) {
        Log.i("Birthday provider", "Going to get events for " + contact);

        Cursor c = ctx.getContentResolver().query(
        contact,
        BIRTHDAY_PROJECTION,
        ContactsContract.Data.MIMETYPE + "= ? AND " +
                "(" + ContactsContract.CommonDataKinds.Event.TYPE + "=" + ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY + " OR " +
                ContactsContract.CommonDataKinds.Event.TYPE + "=" + ContactsContract.CommonDataKinds.Event.TYPE_ANNIVERSARY + " OR " +
                ContactsContract.CommonDataKinds.Event.TYPE + "=" + ContactsContract.CommonDataKinds.Event.TYPE_CUSTOM + " OR " +
                ContactsContract.CommonDataKinds.Event.TYPE + "=" + ContactsContract.CommonDataKinds.Event.TYPE_OTHER + ")",
        new String[]{ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE},
        null);

        List<Event> events = new ArrayList<Event>();
        while (c!= null && c.moveToNext()) {
            try {
                events.add(parseCursor(c));

            } catch (ParseException e) {
                Log.i("BirthdayProvider", "Skipping " + c.getString(0) + " due to unparseable bday date (" + c.getString(2) + ")");
            } catch (IllegalArgumentException e) {
                Log.i("BirthdayProvider", "Skipping " + c.getString(0) + " due to unparseable bday date (" + c.getString(2) + ")");
            } catch (NullPointerException e) {
                Log.i("BirthdayProvider", "Skipping contact id: " + c.getString(1) + " due to NPE.");
            }
        }

        if (c !=null) {
            c.close();
        }
        Log.i("Birthday provider", "Returning " + events);
        return events;
    }

    /**
     *  Returns birthday for given contact.
     * @param ctx
     * @param contact
     * @return
     */
        public ParseResult getBirthday(Context ctx, Uri contact) {
            Log.i("Birthday provider", "Going to get events for " + contact);
            Long id = getIdForContact(ctx, contact);

            if (id != null) {
                Cursor c = ctx.getContentResolver().query(ContactsContract.Data.CONTENT_URI,
                        BIRTHDAY_PROJECTION,
                        ContactsContract.Data.CONTACT_ID + "= ? AND " +
                                ContactsContract.CommonDataKinds.Event.TYPE + "=" + ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY,
                        new String[]{id.toString()},
                        null
                        );
                 if (c!= null && c.moveToFirst()) {
                     try {
                         ParseResult parseResult = tryParseBDay(c.getString(2));
                         return parseResult;
                     } catch (ParseException e) {
                         Log.i("BirthdayProvider", "Cannot parse: " + c.getString(2));
                         return null;
                     } finally {
                         c.close();
                     }
                 }

            }
        return null;
    }

    public Long getIdForContact(Context ctx, Uri contact) {

            Cursor idCursor = ctx.getContentResolver().query(contact, null, null, null, null);
            Long id = null;

            if (idCursor != null && idCursor.moveToFirst()) {
                int idIdx = idCursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID);
                id = idCursor.getLong(idIdx);
            }
            idCursor.close();

        return id;
    }


    public void updateBirthday(Context ctx, Uri contact, LocalDate bDay) {
        Long id = getIdForContact(ctx, contact);

        if (id !=null) {

            ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();
            ops.add(ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
                    .withSelection(ContactsContract.Data.CONTACT_ID + "= ? AND " + ContactsContract.CommonDataKinds.Event.TYPE + "= ?",
                            new String[]{String.valueOf(id), String.valueOf(ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY)})
                    .withValue(ContactsContract.CommonDataKinds.Event.START_DATE, bDay.toString())
                    .build());

            try {
                ContentProviderResult[] result = ctx.getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);
                Log.d("BirthdayProvider", result.toString());
            } catch (RemoteException e) {
                Log.i("BirthdayProvider", "Cannot update birthday", e);
            } catch (OperationApplicationException e) {
                Log.i("BirthdayProvider", "Cannot update birthday", e);
            }

        }
    }


    public List<Event> upcomingBirthday(Context ctx) {
        Log.i("Birthday provider", "Going to get upcoming events");
        long start = System.currentTimeMillis();
        Uri dataUri = ContactsContract.Data.CONTENT_URI;

        Cursor c = ctx.getContentResolver().query(
                dataUri,
                BIRTHDAY_PROJECTION,
                ContactsContract.Data.MIMETYPE + "= ? AND " +
                        "(" + ContactsContract.CommonDataKinds.Event.TYPE + "=" + ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY + " OR " +
                        ContactsContract.CommonDataKinds.Event.TYPE + "=" + ContactsContract.CommonDataKinds.Event.TYPE_ANNIVERSARY + " OR " +
                        ContactsContract.CommonDataKinds.Event.TYPE + "=" + ContactsContract.CommonDataKinds.Event.TYPE_CUSTOM + " OR " +
                        ContactsContract.CommonDataKinds.Event.TYPE + "=" + ContactsContract.CommonDataKinds.Event.TYPE_OTHER + ")",
                new String[]{ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE},
                ContactsContract.Contacts.DISPLAY_NAME);

        Set<Event> result = new TreeSet<Event>();
        while (c!= null && c.moveToNext()) {
            try {
                result.add(parseCursor(c));

            } catch (ParseException e) {
                Log.i("BirthdayProvider", "Skipping " + c.getString(0) + " due to unparseable bday date (" + c.getString(2) + ")");
            } catch (IllegalArgumentException e) {
                Log.i("BirthdayProvider", "Skipping " + c.getString(0) + " due to unparseable bday date (" + c.getString(2) + ")");
            } catch (NullPointerException e) {
                Log.i("BirthdayProvider", "Skipping contact id: " + c.getString(1) + " due to NPE.");
            }
        }
        if (c != null) {
            c.close();
        }
        Log.i("Birthday provider", "Loaded in " + (System.currentTimeMillis() - start) + " [ms]");
        start = System.currentTimeMillis();
        List<Event> result2 = new ArrayList<Event>(result);
        Log.i("Birthday provider", "Converted in " + (System.currentTimeMillis() - start) + "[ms]");
        return result2;
    }

    private Event parseCursor(Cursor c) throws ParseException {
        ParseResult parseResult = tryParseBDay(c.getString(2));
        if (ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY == c.getInt(5)) {
            return new BirthdayEvent(c.getString(0), c.getLong(1),parseResult.date, c.getString(3),
                    parseResult.integrity);
        } else if (ContactsContract.CommonDataKinds.Event.TYPE_ANNIVERSARY == c.getInt(5)) {
            return new AnniversaryEvent(c.getString(0), c.getLong(1),parseResult.date, c.getString(3),
                    parseResult.integrity);
        } else if (ContactsContract.CommonDataKinds.Event.TYPE_CUSTOM == c.getInt(5)) {
            return new CustomEvent(c.getString(0), c.getLong(1),parseResult.date, c.getString(3),
                    parseResult.integrity, c.getString(6));
        } else if (ContactsContract.CommonDataKinds.Event.TYPE_OTHER == c.getInt(5)) {
            return new OtherEvent(c.getString(0), c.getLong(1),parseResult.date, c.getString(3),
                    parseResult.integrity);
        }
        return null;
    }


    public List<EventDebug> allBirthday(Context ctx) {
	  	  Uri dataUri = ContactsContract.Data.CONTENT_URI;
	  	  
	  	  String[] projection = new String[] { ContactsContract.Contacts.DISPLAY_NAME,
	  			  	ContactsContract.CommonDataKinds.Event.CONTACT_ID,
	  			  	ContactsContract.CommonDataKinds.Event.START_DATE,
	  			  	ContactsContract.Contacts.LOOKUP_KEY,
	  			  	ContactsContract.Contacts.PHOTO_ID
	  			  	};
	  	  
	  	  
	  	  Cursor c = ctx.getContentResolver().query(
	  	       dataUri,
	  	       projection, 
	  	       ContactsContract.Data.MIMETYPE + "= ? AND " + 
	  	       ContactsContract.CommonDataKinds.Event.TYPE + "=" + ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY, new String[]{ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE},
	  	       ContactsContract.Contacts.DISPLAY_NAME); 
	  	  
	  	//TODO add reason (Exception message)  
	  	Set<EventDebug> result = new TreeSet<EventDebug>();
	  	while (c!= null && c.moveToNext()) {
	  		ParseResult parseResult;
			try {
				parseResult = tryParseBDay(c.getString(2));
				result.add(new EventDebug(c.getString(0), c.getLong(1),parseResult.date, c.getString(2) , c.getString(3), parseResult.integrity));
			} catch (Exception e) {
				result.add(new EventDebug(c.getString(0), c.getLong(1), null, c.getString(2) , c.getString(3),  DateIntegrity.NONE));
			}
	  		
	  	}
	  	if (c != null) {
	  		c.close();
	  	}
	  	return new ArrayList<EventDebug>(result);
	}

    
	/**
	 * 
	 * @param string
	 * @return
	 * @throws ParseException
	 * @throws IllegalArgumentException
	 */
    public ParseResult tryParseBDay(String string) throws ParseException {
    	if (string == null) {
    		throw new ParseException("Cannot parse: <null>", 0);
    	}
    	
    	if (string.matches(TIMESTAMP_PATTERN)) {
    		LocalDate date = new DateTime(Long.parseLong(string)).withZone(DateTimeZone.UTC).toLocalDate();
    		return new ParseResult(date, DateIntegrity.FULL);
    	}
    	
    	Matcher m;
    	for (DatePattern pat: PATTERNS) {
    		m = Pattern.compile(pat.pattern).matcher(string);
    		if (m.find()) {     			    			    			
    			string = string.substring(m.start(), m.end());
    			LocalDate date = pat.format.withZone(DateTimeZone.UTC).parseDateTime(string).toLocalDate();
    			return new ParseResult(date, pat.integrity);
    		}
    	}
    	
    	throw new ParseException("Cannot parse: " + string, 0);
    	
	}

    public static InputStream openPhoto(Context ctx, long contactId) {
    	Uri contactUri = Uri.withAppendedPath(Contacts.CONTENT_URI, String.valueOf(contactId));
		return Contacts.openContactPhotoInputStream(ctx.getContentResolver(), contactUri);
    }
    
    public static String getPhoneNumber(Context ctx, long id) {
        Cursor cursor = ctx.getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                new String[]{
        			ContactsContract.CommonDataKinds.Phone.NUMBER},
        			ContactsContract.CommonDataKinds.Phone.CONTACT_ID + "=" + id 
        				+ " AND " + ContactsContract.CommonDataKinds.Phone.TYPE 
        				+ "=" + ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
        				,null, null);
          
        
        try {
            if (cursor.moveToFirst()) {
                 return cursor.getString(0);
            } else {
            	return null;
            }
        } finally {
            cursor.close();
        }
    }
        
    public static String getEmail(Context ctx, long id) {
        Cursor cursor = ctx.getContentResolver().query(ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                new String[]{
        			ContactsContract.CommonDataKinds.Email.DATA},
        			ContactsContract.CommonDataKinds.Phone.CONTACT_ID + "=" + id        				
        				,null, null);
          
        
        try {
            if (cursor.moveToFirst()) {
                 return cursor.getString(0);
            } else {
            	return null;
            }
        } finally {
            cursor.close();
        }
    }

    
    class DatePattern {
    	String pattern;
    	DateTimeFormatter format;
    	DateIntegrity integrity;
    	
    	public DatePattern(String pattern, String format, DateIntegrity integrity) {
    		this.pattern = pattern;    		
    		this.format = DateTimeFormat.forPattern(format);
    		this.integrity = integrity;
		}
    	
    	
    }
    
}
