
package com.code_test;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

import java.util.List;
import java.util.Locale;

import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import java.time.LocalDateTime;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import org.json.*;

//Johnny LAI

public class App {

	private static final Logger LOGGER = Logger.getLogger(App.class.getName());
	private static String DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss";
	private static String INPUT_FILENAME = "test_data.csv";
	private static String OUTPUT_FILENAME = "test_output.csv";
	public static void main(String[] args) {

		String csvFile = INPUT_FILENAME;
		FileHandler fh;
		String line = "";
		int lineNum = 1;

		String cvsSplitBy = ",(?=([^\"]*\"[^\"]*\")*[^\"]*$)";

		List<VehicleInfo> vehicleInfoList = new ArrayList<VehicleInfo>();

		try {
			fh = new FileHandler("App.log", true);
			LOGGER.addHandler(fh);
			SimpleFormatter formatter = new SimpleFormatter();
			fh.setFormatter(formatter);
		} catch (IOException e) {
			e.printStackTrace();
		}

		try (BufferedReader br = new BufferedReader(new FileReader(csvFile))) {

			while ((line = br.readLine()) != null) {
				
				//if empty line, then skip
				if (line.trim().length() == 0)	{
					continue;
				}

				String[] country = line.split(cvsSplitBy);

				String dateTime;
				String latitude;
				String longitude;

				dateTime = country[0];
				latitude = country[1];
				longitude = country[2];

				if (!isValidFormat(dateTime)) {
					LOGGER.severe("********** Line " + lineNum + " in CSV: Date Time value invalid **********");
					throw new Exception();
				}
				if (Double.parseDouble(latitude) < -90 || Double.parseDouble(latitude) > 90) {
					LOGGER.severe("********** Line " + lineNum + " in CSV: Latitude value invalid **********");
					throw new Exception();
				}
				if (Double.parseDouble(longitude) < -180 || Double.parseDouble(longitude) > 180) {
					LOGGER.severe("********** Line " + lineNum + " in CSV: Longitude value invalid **********");
					throw new Exception();
				}

				VehicleInfo vehicleInfo = new VehicleInfo(dateTime, latitude, longitude);
				vehicleInfoList.add(vehicleInfo);
				retrieveTimeZoneName(lineNum, vehicleInfo);

				lineNum++;
			}
			br.close();
			
			writeCSV(vehicleInfoList);

		} catch (IOException e) {
			LOGGER.severe("********** Problem in reading CSV file **********");
			LOGGER.severe(e.toString());
			System.exit(1);
		} catch (ArrayIndexOutOfBoundsException e) {
			LOGGER.severe("********** Line " + lineNum + " in CSV: missing information or format incorrect **********");
			LOGGER.severe(e.toString());
			System.exit(1);
		} catch (Exception e) {
			System.out.println("********** Please check log file for detail information **********");
			LOGGER.severe(e.toString());
			System.exit(1);
		}

		System.exit(0);
	}

	//Retrieve TimeZone name from web service API
	public static void retrieveTimeZoneName(int lineNum, VehicleInfo vehicleInfo) throws Exception {
		try {

			URL url = new URL("http://api.geonames.org/timezoneJSON?formatted=true&lat=" + vehicleInfo.getLatitude()
					+ "&lng=" + vehicleInfo.getLongitude() + "&username=ajohnny&style=full");
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("GET");
			conn.setRequestProperty("Accept", "application/json");

			if (conn.getResponseCode() != 200) {
				throw new RuntimeException(
						"********** Failed : HTTP error code : " + conn.getResponseCode() + " **********");
			}
			StringBuilder sb = new StringBuilder();
			BufferedReader br = new BufferedReader(new InputStreamReader((conn.getInputStream())));

			String output;

			while ((output = br.readLine()) != null) {
				sb.append(output);

			}
			//Read JSON output
			JSONObject json = new JSONObject(sb.toString());
			String timeZone = "";
			String localTime = "";
			DateTimeFormatter fomatter = DateTimeFormatter.ofPattern(DATE_TIME_FORMAT, Locale.ENGLISH);
			//Parse inputted datetime with UTC time
			ZonedDateTime utcDateTime = LocalDateTime.parse(vehicleInfo.getDateTime(), fomatter).atZone(ZoneOffset.UTC); 
			try {
				//convert to local date time based on timezone name retrieved from web service API
				timeZone = json.getString("timezoneId");
				ZoneId localTimeZone = ZoneId.of(timeZone);
				ZonedDateTime localZone = utcDateTime.withZoneSameInstant(localTimeZone);
				LocalDateTime localTimeObj = localZone.toLocalDateTime();
				localTime = localTimeObj.toString();
			} catch (JSONException e) {
				try {
					//Unable to get timezone name, probably server error, try to retrieve error msg from Json output
					LOGGER.severe("********** Message from API server - api.geonames.org: "+ json.getJSONObject("status").getString("message") + " **********");
					LOGGER.severe(e.toString());
					throw new Exception();
				}	catch (JSONException js)	{
						try	{
							//Unable to get either timezone name or error message, try to get raw offset for some special cases which without timezone id
							//Convert time to local time based on raw offset retrieved from web service API
							int timeZoneOffset = json.getInt("rawOffset");
							ZoneOffset offset = ZoneOffset.ofHours(timeZoneOffset);					
							LocalDateTime localTimeObj = LocalDateTime.ofInstant(utcDateTime.toInstant(), offset);
							System.out.println("with offset " + localTimeObj.toString());
							LOGGER.severe("********** Line " + lineNum + " in CSV: this location do not have time zone name, local date time will be converted by raw offset**********");
							timeZone = "Timezone name is not available for this location (offset: " + offset + ")";
							localTime = localTimeObj.toString();
						}	catch (JSONException jss)	{
							//Unexpected case!
							timeZone = "Timezone name is not available for this location";
							localTime = "Local date time is not available for this location";
							LOGGER.severe("********** Line " + lineNum + " in CSV: this location do not have time zone and local date time **********");	
						}				
				}

			}
	
			vehicleInfo.setTimeZone(timeZone);
			vehicleInfo.setLocalDateTime(localTime);
			conn.disconnect();

		} catch (MalformedURLException e) {
			LOGGER.severe("********** Problem in the API URL, fail to retrieve Timezone / Local Date Time information from API **********");
			LOGGER.severe(e.toString());
			throw new Exception();
		} catch (IOException e) {

			LOGGER.severe("********** Problem in reading the API output, fail to retrieve Timezone / Local Date Time information from API **********");
			LOGGER.severe(e.toString());
			throw new Exception();
		}

	}

	public static void writeCSV(List vehicleInfoList) {

		File fout = new File(OUTPUT_FILENAME);
		FileOutputStream fos = null;
		try {
			fos = new FileOutputStream(fout);
		} catch (FileNotFoundException e) {
			LOGGER.severe("********** Unable to create a file for CSV output, please check system permission **********");
			LOGGER.severe(e.toString());
		}
	 
		BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(fos));		
		try {
			for (int i = 0; i < vehicleInfoList.size(); i++) {
					//bw.write(vehicleInfoList.get(i).toString()+"\n");
					bw.write(vehicleInfoList.get(i).toString());
					//User .newLine() to compatible with both MacOS and Windows
					bw.newLine();
					bw.newLine();
			}
			bw.close();
			fos.close();
		} catch (IOException e) {
			LOGGER.severe("********** Unable to write the CSV output file, please check system permission **********");
			LOGGER.severe(e.toString());
		}		

	}

	public static boolean isValidFormat(String value) {
		LocalDateTime ldt = null;

		
		DateTimeFormatter fomatter = DateTimeFormatter.ofPattern(DATE_TIME_FORMAT, Locale.ENGLISH);
	
		try {
			ldt = LocalDateTime.parse(value, fomatter);
			String result = ldt.format(fomatter);
			return result.equals(value);
		} catch (DateTimeParseException e) {
			return false;
		}
	
	}	
}

class VehicleInfo	{
		String dateTime;
		String latitude;
		String longitude;
		String timeZone;
		String localDateTime;

		public VehicleInfo(String dateTime, String latitude, String longitude)	{
			this.dateTime = dateTime;
			this.latitude = latitude;
			this.longitude = longitude;
		}	

		public String getDateTime() {
		return dateTime;
		}


		public void setDateTime(String dateTime) {
		this.dateTime = dateTime;
		}


		public String getLatitude() {
		return latitude;
		}


		public void setLatitude(String latitude) {
		this.latitude = latitude;
		}


		public String getLongitude() {
		return longitude;
		}
	
	
		public void setLongitude(String longitude) {
			this.longitude = longitude;
		}
	
		public String getLocalDateTime() {
			return localDateTime;
		}	
		
		public void setLocalDateTime(String localDateTime) {
			this.localDateTime = localDateTime;
		}	
		
		public String getTimeZone() {
			return timeZone;
		}	
		
		public void setTimeZone(String timeZone) {
			this.timeZone = timeZone;
		}			

		public String toString()	{
			return dateTime + "," + latitude + "," + longitude + "," + timeZone + "," + localDateTime;
		}
} 