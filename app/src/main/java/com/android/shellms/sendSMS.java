/*	
    ShellMS - Android Debug Bridge Shell SMS Application
	https://github.com/try2codesecure/ShellMS
	
    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

package com.android.shellms;

import java.util.ArrayList;

import android.net.Uri;
import android.os.Bundle;
import android.app.PendingIntent;
import android.app.Service;
import android.telephony.PhoneNumberUtils;
import android.telephony.SmsManager;
import android.util.Log;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.os.IBinder;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Data;

public class sendSMS extends Service {    

	private static final String TAG = "ShellMS_Service_sendSMS";
	private static final String TELEPHON_NUMBER_FIELD_NAME = "address";
    private static final String MESSAGE_BODY_FIELD_NAME = "body";
    private static final Uri SENT_MSGS_CONTET_PROVIDER = Uri.parse("content://sms/sent");
	boolean DEBUG = false;	// debug mode, display additional output, sends no sms.

	// This is the start function for the service.
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		boolean SECRET = false;	// for secret mode => dont't save sent sms to sent folder.
		String contact = null;
		String val_num = null;	// validated Number
		String msg = null;		// message
		boolean valid = false;	// for user input validation
		int check = 0;			// getExtras check counter

		// extract and validate the extra strings from the service start
		Bundle extras = intent.getExtras();
		if ( extras != null ) {
			if ( extras.containsKey("debug") ) {
				DEBUG = true;
				Log.d(TAG, "DEBUG Mode enabled" );
			}
			if ( extras.containsKey("secret") ) {
				SECRET = true;
			}
			if ( extras.containsKey("contact") ) {
				contact = extras.getString("contact");
				if (contact!=null)	{
					check++;
				}
			}
			if ( extras.containsKey("msg") ) {
				msg = extras.getString("msg");
				if (msg!=null)	{
					check++;
				}
			}
			if (check == 2)	{
				if (DEBUG)	{
					Log.d(TAG, contact);
				}
				// search for valid telephone number
				valid = isNumberValid(contact);

				// otherwise search for valid contact names in database
				if (!valid)	{
					if (DEBUG)	{
						Log.d(TAG, "Error: Can't validate mobile number: " + contact);
						Log.d(TAG, "try searching in contacts database ...");
					}

					val_num = getNumberfromContact(contact, DEBUG);
					if (val_num != null)	{
						contact = val_num;
						valid = true;
						if (DEBUG)	{
							Log.d(TAG, "found contact: " + contact );
						}
					} else	{
						Log.e(TAG, "Error: No valid mobile number for contact " + contact);
					}
				}
				if (valid)	{
					if (!DEBUG)	{
						sendsms(contact, msg, !SECRET);
						Log.i(TAG, "Sent SMS to contact: " + contact );
					} else	{
						Log.d(TAG, "NO MESSAGE WILL BE SENT IN DEBUG MODE" );
						Log.d(TAG, "Contact: " + contact );
						Log.d(TAG, "Message: " + msg);
					}
				} else	{
					Log.e(TAG, "Unknown Error occoured with contact: " + contact);
				}
			} else {
				Log.e(TAG, "Error: Contact or Message missing" );
			}
		}
		stopSelf();
		return Service.START_STICKY;
	}

	// User input validation
	private Boolean isNumberValid(String contact)	{
		if (contact == null)	{
			return false;
		}
		boolean valid1 = PhoneNumberUtils.isGlobalPhoneNumber(contact);
		boolean valid2 = PhoneNumberUtils.isWellFormedSmsAddress(contact);
        return (valid1) && (valid2);
    }
	private String makeNumberValid(String contact)	{
		if (contact == null)	{
			return null;
		}
		String number = PhoneNumberUtils.normalizeNumber(contact);
		if (DEBUG)	{
			Log.e(TAG, "corrected number: " + number );
		}
		boolean valid = isNumberValid(number);
		if (valid)	{
			return number;
		}
		return null;
	}

	// This function searches for an mobile phone entry for the contact
	private String getNumberfromContact(String contact, Boolean debugging)	{
		ContentResolver cr = getContentResolver();
		String result = null;
		boolean valid = false;
		String val_num = null;
		int contact_id = 0;
        // Cursor1 search for valid Database Entries who matches the contact name
		Uri uri = ContactsContract.Contacts.CONTENT_URI;
		String[] projection = new String[]{	ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME, ContactsContract.Contacts.HAS_PHONE_NUMBER };
		String selection = ContactsContract.Contacts.DISPLAY_NAME + "=?";
		String[] selectionArgs = new String[]{String.valueOf(contact)};
		Cursor cursor1 = cr.query(uri, projection, selection, selectionArgs, null);

        assert cursor1 != null;
        if(cursor1.moveToFirst()){
	    	if(cursor1.getInt(cursor1.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)) == 1){
	    		contact_id = cursor1.getInt(cursor1.getColumnIndex(ContactsContract.Contacts._ID));
	    		if (debugging)	{
	        		Log.d(TAG, "C1 found Database ID: " + contact_id + " with Entry: " + cursor1.getString(cursor1.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)));
	            }
	            // Cursor 2 search for valid MOBILE Telephone numbers (selection = Phone.TYPE 2)
	        	Uri uri2 = ContactsContract.Data.CONTENT_URI;	
	        	String[] projection2 = new String[]{ Phone.NUMBER, Phone.TYPE };
	        	String selection2 = Phone.CONTACT_ID + "=? AND " + Data.MIMETYPE + "=? AND " + Phone.TYPE + "=2";
	    		String[] selectionArgs2 = new String[]{ String.valueOf(contact_id), Phone.CONTENT_ITEM_TYPE };
	    		String sortOrder2 = Data.IS_PRIMARY + " desc"; 	
	        	Cursor cursor2 = cr.query(uri2, projection2, selection2, selectionArgs2, sortOrder2);

                assert cursor2 != null;
                if(cursor2.moveToFirst()){
	                result = cursor2.getString(cursor2.getColumnIndex(Phone.NUMBER));
	        		if (debugging)	{
	                	Log.d(TAG, "C2 found number: " + result);
	                }
	            }
	            cursor2.close();
	        }
	        cursor1.close();
	    }
	    if (result != null)	{
	    	valid = isNumberValid(result);
	    }
		if (!valid)	{
			if (debugging)	{
            	Log.d(TAG, "number seems invalid, try to resolve: " + result);
            }
			val_num = makeNumberValid(result);
			if (val_num != null)	{
				valid = true;
				result = val_num;
				if (debugging)	{
	            	Log.d(TAG, "return modified number: " + result);
	            }
			}
		}
	    if (valid)	{
	    	if (debugging)	{
            	Log.d(TAG, "return number: " + result);
            }
	    	return result;
	    } else	{
	    	return null;
	    }
	}

	// This function sends the sms with the SMSManager
	private void sendsms(final String phoneNumber, final String message, final Boolean AddtoSent)	{
		try {
			String SENT = TAG + "_SMS_SENT";
			Intent myIntent = new Intent(SENT);
	    	PendingIntent sentPI = PendingIntent.getBroadcast(this, 0, myIntent, 0);
	        
	    	SmsManager sms = SmsManager.getDefault();
	        ArrayList<String> msgparts = sms.divideMessage(message);
	    	ArrayList<PendingIntent> sentPendingIntents = new ArrayList<>();
	    	int msgcount = msgparts.size();

	    	for (int i = 0; i < msgcount; i++) {
	            sentPendingIntents.add(sentPI);
	        }

	    	sms.sendMultipartTextMessage(phoneNumber, null, msgparts, sentPendingIntents, null);
	        if (AddtoSent)	{
				addMessageToSent(phoneNumber, message);
			}
		} catch (Exception e) {
	        e.printStackTrace();
	        Log.e(TAG, "undefined Error: SMS sending failed ... please REPORT to ISSUE Tracker");
	    }
    }
	// This function add's the sent sms to the SMS sent folder
	private void addMessageToSent(String phoneNumber, String message) {
        ContentValues sentSms = new ContentValues();
        sentSms.put(TELEPHON_NUMBER_FIELD_NAME, phoneNumber);
        sentSms.put(MESSAGE_BODY_FIELD_NAME, message);
        
        ContentResolver contentResolver = getContentResolver();
        contentResolver.insert(SENT_MSGS_CONTET_PROVIDER, sentSms);
	}
	@Override
	public IBinder onBind(Intent intent) { return null;	}
}