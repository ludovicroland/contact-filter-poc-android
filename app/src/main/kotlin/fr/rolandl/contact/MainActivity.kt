package fr.rolandl.contact

import android.Manifest
import android.content.ContentResolver
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.support.v4.view.MenuItemCompat
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.SearchView
import android.telephony.PhoneNumberUtils
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.ArrayAdapter
import kotlinx.android.synthetic.main.activity_main.*
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import permissions.dispatcher.NeedsPermission
import permissions.dispatcher.RuntimePermissions
import java.io.Serializable


/**
 * @author Ludovic Roland
 * @since 2017.05.22
 */
@RuntimePermissions
class MainActivity : AppCompatActivity(), SearchView.OnQueryTextListener, MenuItemCompat.OnActionExpandListener
{
  private data class Contact(val name: String, val phones: MutableList<String>) : Serializable
  {

    override fun toString(): String
    {
      val builder = StringBuilder(name)
      builder.append("\n")
      phones.forEach {
        builder.append(it).append("\n")
      }

      return builder.toString()
    }

  }

  private lateinit var adapter: ArrayAdapter<MainActivity.Contact>

  override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray)
  {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    MainActivityPermissionsDispatcher.onRequestPermissionsResult(this, requestCode, grantResults)
  }

  override fun onCreate(savedInstanceState: Bundle?)
  {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    MainActivityPermissionsDispatcher.retrieveContactsWithCheck(this, contentResolver)
  }

  override fun onCreateOptionsMenu(menu: Menu?): Boolean
  {
    menuInflater.inflate(R.menu.search, menu)
    val menuItem = menu?.findItem(R.id.search)
    val sv = MenuItemCompat.getActionView(menuItem) as SearchView
    sv.setOnQueryTextListener(this)
    MenuItemCompat.setOnActionExpandListener(menuItem, this)

    return super.onCreateOptionsMenu(menu)
  }

  @NeedsPermission(Manifest.permission.READ_CONTACTS)
  fun retrieveContacts(contentResolver: ContentResolver)
  {
    val start = System.currentTimeMillis()

    doAsync {
      val contacts = mutableListOf<Contact>()
      val contactCursor = contentResolver.query(ContactsContract.Contacts.CONTENT_URI, arrayOf(ContactsContract.Data.DISPLAY_NAME, ContactsContract.Data._ID, ContactsContract.Contacts.HAS_PHONE_NUMBER), null, null, null)

      if (contactCursor == null)
      {
        Log.e("Contacts", "Cannot retrieve the contacts")
      }

      if (contactCursor.moveToFirst() === true)
      {
        do
        {
          val id = contactCursor.getLong(contactCursor.getColumnIndex(ContactsContract.Data._ID))
          val name = contactCursor.getString(contactCursor.getColumnIndex(ContactsContract.Data.DISPLAY_NAME))
          val hasPhoneNumber = contactCursor.getInt(contactCursor.getColumnIndex(ContactsContract.Data.HAS_PHONE_NUMBER))

          if (hasPhoneNumber > 0)
          {
            val phoneNumbers = mutableListOf<String>()
            val phoneCursor = contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null, ContactsContract.CommonDataKinds.Phone.CONTACT_ID + "=" + id, null, null)

            if (phoneCursor.moveToFirst() === true)
            {
              do
              {
                phoneNumbers.add(PhoneNumberUtils.normalizeNumber(phoneCursor.getString(phoneCursor.getColumnIndex(Phone.NUMBER))))
              }
              while (phoneCursor.moveToNext() === true)
            }

            if (phoneCursor.isClosed === false)
            {
              phoneCursor.close()
            }

            contacts.add(Contact(name, phoneNumbers))
          }
        }
        while (contactCursor.moveToNext() === true)
      }

      if (contactCursor.isClosed === false)
      {
        contactCursor.close()
      }

      Log.d("Contacts", "Retrieve ${contacts.size} contacts in ${System.currentTimeMillis() - start} ms")

      uiThread {
        displayContacts(contacts)
      }
    }

  }

  private fun displayContacts(contacts: MutableList<Contact>)
  {
    val start = System.currentTimeMillis()
    adapter = ArrayAdapter<Contact>(this, android.R.layout.simple_list_item_1, contacts)
    list.adapter = adapter
    Log.d("Contacts", "Display contacts in ${System.currentTimeMillis() - start} ms")
  }

  override fun onMenuItemActionExpand(item: MenuItem?): Boolean
  {
    resetFilter()
    return true
  }

  override fun onMenuItemActionCollapse(item: MenuItem?): Boolean
  {
    resetFilter()
    return true
  }

  override fun onQueryTextSubmit(query: String?): Boolean
  {
    return false
  }

  override fun onQueryTextChange(newText: String?): Boolean
  {
    adapter.filter.filter(newText)
    return true
  }

  private fun resetFilter()
  {
    adapter.filter.filter("")
  }

}