package com.ropherpanama.locationmapper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

import com.shinetech.android.R;

import android.app.ListActivity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.database.Cursor;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Looper;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.SimpleCursorAdapter;
import android.widget.Toast;

public class ShowStoredLocationActivity extends ListActivity {
	private static final String TAG = "LocationTrackerActivity";
	private LocationDbAdapter dbAdapter;
	private SimpleCursorAdapter cursorAdapter;

	public SimpleCursorAdapter getCursorAdapter() {
		return cursorAdapter;
	}

	public void setCursorAdapter(SimpleCursorAdapter cursorAdapter) {
		this.cursorAdapter = cursorAdapter;
	}

	private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			Log.i(TAG, "Received UPDATE_UI message");
			getCursorAdapter().getCursor().requery();
			getCursorAdapter().notifyDataSetChanged();
		}
	};

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.stored_locations);
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

		dbAdapter = new LocationDbAdapter(this);

//		ComponentName locationListenerServiceName = new ComponentName(getPackageName(), LocationListenerService.class.getName());
//		Intent service = new Intent(getApplicationContext(), LocationListenerService.class);
//		startService(new Intent().setComponent(locationListenerServiceName));
//		service.putExtra("DISTANCE_INTERVAL", 5);
//		service.putExtra("TIME_INTERVAL", 10);
//		service.putExtra("RADIUS_INTERVAL", 1);
//		startService(service);
		
	}

	@Override
	public void onResume() {
		Log.i(TAG, "onResume()");
		super.onResume();
		dbAdapter.open();
		String[] from = { LocationDbAdapter.KEY_NAME,
				          LocationDbAdapter.KEY_LATITUDE,
				          LocationDbAdapter.KEY_LONGITUDE,
				          LocationDbAdapter.KEY_ACCURACY, 
				          LocationDbAdapter.KEY_TIME };
		
		int[] to = { R.id.locationname, R.id.locationlatitude,
				     R.id.locationlongitude, R.id.locationaccuracy,
				     R.id.locationtime };
		//Coloca lo que este en la base de datos en la pantalla
		cursorAdapter = new LocationCursorAdapter(this, R.layout.stored_locations_row_layout, dbAdapter.fetchAllLocations(), from, to);
		this.setListAdapter(cursorAdapter);
		
		//Actualizacion de GUI cuando se captura una nueva coordenada
		IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction("com.shinetech.android.UPDATE_UI");
		this.registerReceiver(this.broadcastReceiver, intentFilter);
		
		getCursorAdapter().getCursor().requery();
		getCursorAdapter().notifyDataSetChanged();
	}

	@Override
	public void onPause() {
		Log.i(TAG, "onPause()");
		super.onPause();
		dbAdapter.close();
		this.unregisterReceiver(this.broadcastReceiver);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.options_menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.settings:
//			startActivity(new Intent(this, ShowLocationSettingsActivity.class));
			enviarDatos();
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}
	
	private void enviarDatos() {
		EmailDataTask emailDataTask = new EmailDataTask();
		emailDataTask.execute();
	}
	
	private class EmailDataTask extends AsyncTask<String, Void, String> {
		@Override
		protected String doInBackground(String... urls) {
			Looper.prepare();
			try {
				writeDbDataToTempFile();
				emailData();
			} catch (Exception e) {
				e.printStackTrace();
				return "Error enviando datos: " + e.getMessage();
			}
			return "Enviar email";
		}

		@Override
		protected void onPostExecute(String result) {
			Toast.makeText(getApplicationContext(), result, Toast.LENGTH_SHORT).show();;
		}

		private void emailData() {
			final Intent emailIntent = new Intent(android.content.Intent.ACTION_SEND_MULTIPLE);
			emailIntent.setType("plain/text");
			emailIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			emailIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, "Datos de coordenadas recibidos");
			emailIntent.putExtra(android.content.Intent.EXTRA_TEXT, "Fichero adjunto de coordenadas");

			ArrayList<Uri> uris = new ArrayList<Uri>();

			File fileIn = new File(Environment.getExternalStorageDirectory(), "coordenadas.csv");
			Uri u = Uri.fromFile(fileIn);
			uris.add(u);
			emailIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
//			getApplicationContext().startActivity(Intent.createChooser(emailIntent, "Enviando mail ..."));
			startActivity(Intent.createChooser(emailIntent, "Enviando mail ..."));
		}

		private void writeDbDataToTempFile() throws IOException {
			String data = "";
			File dataFile = new File(Environment.getExternalStorageDirectory(), "coordenadas.csv");
			FileOutputStream fileOutputStream = new FileOutputStream(dataFile);
			OutputStreamWriter outputStreamWriter = new OutputStreamWriter(fileOutputStream);
			Cursor cursor = dbAdapter.fetchAllLocations();
			cursor.moveToFirst();

			while (cursor.isAfterLast() == false) {
				data = cursor.getDouble(2) + "," //lat
						+ cursor.getDouble(3) + ","//lon
						+ cursor.getDouble(4) + ","//alt
						+ cursor.getInt(5) + ","//acc
						+ cursor.getString(1) + ","//name
						+ new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date(cursor.getLong(6))) + "\n";//time
				
				outputStreamWriter.write(data);
				cursor.moveToNext();
			}
			outputStreamWriter.flush();
			outputStreamWriter.close();
			cursor.close();
			Toast.makeText(getApplicationContext(), "Hecho!", Toast.LENGTH_SHORT).show();
		}
	}
	
	public void onClearDataBase(View view) {
		int rows = dbAdapter.clearDatabase();
		getCursorAdapter().getCursor().requery();
		getCursorAdapter().notifyDataSetChanged();
		Toast.makeText(this, "Se borraron " + rows + " registros.", Toast.LENGTH_SHORT).show();
	}
	
	public void iniciarServicio(View v) {
//		ComponentName locationListenerServiceName = new ComponentName(getPackageName(), LocationListenerService.class.getName());
		Intent service = new Intent(getApplicationContext(), LocationListenerService.class);
//		startService(new Intent().setComponent(locationListenerServiceName));
		service.putExtra("DISTANCE_INTERVAL", 5);
		service.putExtra("TIME_INTERVAL", 10);
		service.putExtra("RADIUS_INTERVAL", 1);
		startService(service);
	}
	
	public void detenerServicio(View v) {
		stopService(new Intent(ShowStoredLocationActivity.this, LocationListenerService.class));
		Toast.makeText(getApplicationContext(), "Servicio detenido", Toast.LENGTH_LONG).show();
	}
	
	public void capturarManual(View v) {
		LocationManager lm = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
	    Location location = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
		location.setProvider("manual");
		dbAdapter.addLocation(location);
		Toast.makeText(getApplicationContext(), "Captura manual guardada", Toast.LENGTH_LONG).show();
		getCursorAdapter().getCursor().requery();
		getCursorAdapter().notifyDataSetChanged();
	}
}