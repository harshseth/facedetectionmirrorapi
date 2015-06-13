/*
 * Copyright (C) 2013 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */



package com.google.glassware;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.repackaged.org.apache.commons.codec.binary.Base64;
import com.google.api.services.mirror.Mirror;
import com.google.api.services.mirror.model.Attachment;
import com.google.api.services.mirror.model.Location;
import com.google.api.services.mirror.model.MenuItem;
import com.google.api.services.mirror.model.Notification;
import com.google.api.services.mirror.model.NotificationConfig;
import com.google.api.services.mirror.model.TimelineItem;
import com.google.api.services.mirror.model.UserAction;
import com.google.common.collect.Lists;
import com.mashape.unirest.http.JsonNode;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Map.Entry;
import java.util.logging.Logger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

import javax.imageio.ImageIO;
//import javax.imageio.ImageIO;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.apache.commons.io.*;


/**
 * Handles the notifications sent back from subscriptions
 *
 * @author Jenny Murphy - http://google.com/+JennyMurphy
 */
public class NotifyServlet extends HttpServlet {
  private static final Logger LOG = Logger.getLogger(NotifyServlet.class.getSimpleName());

  private static final String[] CAT_UTTERANCES = {
      "<em class='green'>Purr...</em>",
      "<em class='red'>Hisss... scratch...</em>",
      "<em class='yellow'>Meow...</em>"
  };

  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    // Respond with OK and status 200 in a timely fashion to prevent redelivery
    response.setContentType("text/html");
    Writer writer = response.getWriter();
    writer.append("OK");
    writer.close();

    // Get the notification object from the request body (into a string so we
    // can log it)
    BufferedReader notificationReader =
        new BufferedReader(new InputStreamReader(request.getInputStream()));
    String notificationString = "";
    String fileUploadURL = null;
    String responseStringForFaceDetection = null ;

    //#### defining the HashMap required for the property detection

    Map<String, List<String>> propertyMapForFaceDetection = new HashMap<String, List<String>>();

    //note: The list should contain only the plurals, eg: females and not female. The singular will be taken care in the code.
    List<String> genderPorperty = new ArrayList<String>();
    genderPorperty.add("males");
    genderPorperty.add("females");
    genderPorperty.add("gender");
    genderPorperty.add("men");
    genderPorperty.add("women");
    genderPorperty.add("girls");
    genderPorperty.add("boys");
    //excpetion for plural rule
    genderPorperty.add("man");
    genderPorperty.add("woman");

    List<String> agePorperty = new ArrayList<String>();
    agePorperty.add("age");
    agePorperty.add("kids");
    agePorperty.add("adults");
    agePorperty.add("old");
    agePorperty.add("young");
    agePorperty.add("children");

    List<String> emotionPorperty = new ArrayList<String>();
    emotionPorperty.add("emotion");
    emotionPorperty.add("happy");
    emotionPorperty.add("sad");
    emotionPorperty.add("angry");
    emotionPorperty.add("safe");
    emotionPorperty.add("calm");
    emotionPorperty.add("confused");
    emotionPorperty.add("disgusted");

    List<String> conversationPorperty = new ArrayList<String>();
    conversationPorperty.add("conversation");
    conversationPorperty.add("conversing");
    conversationPorperty.add("talking");
    conversationPorperty.add("chatting");
    conversationPorperty.add("speaking");

    List<String> distancePorperty = new ArrayList<String>();
    distancePorperty.add("distance");
    distancePorperty.add("nearest");
   // distancePorperty.add("far");
    distancePorperty.add("closest");
    distancePorperty.add("farthest");

    List<String> sleepingPorperty = new ArrayList<String>();
    sleepingPorperty.add("awake");
    sleepingPorperty.add("sleeping");
    //sleepingPorperty.add("sleep");

    propertyMapForFaceDetection.put("gender",genderPorperty);
    propertyMapForFaceDetection.put("age",agePorperty);
    propertyMapForFaceDetection.put("emotion",emotionPorperty);
    propertyMapForFaceDetection.put("conversation",conversationPorperty);
    propertyMapForFaceDetection.put("distance",distancePorperty);
    propertyMapForFaceDetection.put("sleeping",sleepingPorperty);


    //### end of HashMap definition

    // Count the lines as a very basic way to prevent Denial of Service attacks
    int lines = 0;
    String line;
    while ((line = notificationReader.readLine()) != null) {
      notificationString += line;
      lines++;

      // No notification would ever be this long. Something is very wrong.
      if (lines > 1000) {
        throw new IOException("Attempted to parse notification payload that was unexpectedly long.");
      }
    }
    notificationReader.close();

    LOG.info("got raw notification " + notificationString);

    JsonFactory jsonFactory = new JacksonFactory();

    // If logging the payload is not as important, use
    // jacksonFactory.fromInputStream instead.
    Notification notification = jsonFactory.fromString(notificationString, Notification.class);

    LOG.info("Got a notification with ID: " + notification.getItemId());

    // Figure out the impacted user and get their credentials for API calls
    String userId = notification.getUserToken();
    Credential credential = AuthUtil.getCredential(userId);
    Mirror mirrorClient = MirrorClient.getMirror(credential);


    if (notification.getCollection().equals("locations")) {
      LOG.info("Notification of updated location");
      Mirror glass = MirrorClient.getMirror(credential);
      // item id is usually 'latest'
      Location location = glass.locations().get(notification.getItemId()).execute();

      LOG.info("New location is " + location.getLatitude() + ", " + location.getLongitude());
      MirrorClient.insertTimelineItem(
          credential,
          new TimelineItem()
              .setText("Java Quick Start says you are now at " + location.getLatitude()
                  + " by " + location.getLongitude())
              .setNotification(new NotificationConfig().setLevel("DEFAULT")).setLocation(location)
              .setMenuItems(Lists.newArrayList(new MenuItem().setAction("NAVIGATE"))));

      // This is a location notification. Ping the device with a timeline item
      // telling them where they are.
    } else if (notification.getCollection().equals("timeline")) {
      // Get the impacted timeline item
      TimelineItem timelineItem = mirrorClient.timeline().get(notification.getItemId()).execute();
      LOG.info("Notification impacted timeline item with ID: " + timelineItem.getId());

      // If it was a share, and contains a photo, update the photo's caption to
      // acknowledge that we got it.
      if (notification.getUserActions().contains(new UserAction().setType("SHARE"))
          && timelineItem.getAttachments() != null && timelineItem.getAttachments().size() > 0) {

    	 String questionString = timelineItem.getText();
    	 String[] questionStringArray = questionString.split(" ");

    	 LOG.info( timelineItem.getText()+" is the questions asked by the user");
    	// LOG.info((timelineItem.getText()==null)+" ");
        LOG.info("A picture was taken");
        //LOG.info(timelineItem.getAttachments().toString());

        // Uploading the image to a public server and obtaining the respective URL
        InputStream inputStream = downloadAttachment(mirrorClient, notification.getItemId(), timelineItem.getAttachments().get(0));


        //-------------------------code for converting the Image to  Base64 ---------------

        Base64 base64Object = new Base64(false);
        String encodedImageToBase64 = base64Object.encodeToString(IOUtils.toByteArray(inputStream)); //byteArrayForOutputStream.toByteArray()
       // byteArrayForOutputStream.close();
        encodedImageToBase64 = java.net.URLEncoder.encode(encodedImageToBase64, "ISO-8859-1");






          //########################################## API CODE SECTION ###########################################
          LOG.info("Sending request to API");
         //For initial protoype we're calling the Alchemy API for detecting the number of Faces using web API call
         try{


              String urlParameters  = "api_key=gE4P9Mze0ewOa976&api_secret=96JJ4G1bBLPaWLhf&jobs=face_gender_age_emotion_recognize_stats_mouth_open_wide_eye_closed&base64="+encodedImageToBase64;
              byte[] postData       = urlParameters.getBytes( Charset.forName( "UTF-8" ));
              int    postDataLength = postData.length;
              String newrequest        = "https://orbeus-rekognition.p.mashape.com/";
              URL    url            = new URL( newrequest );
              HttpURLConnection connectionFaceDetection= (HttpURLConnection) url.openConnection();

              // Increase the timeout for reading the response
              connectionFaceDetection.setReadTimeout(15000);

              connectionFaceDetection.setDoOutput( true );
              connectionFaceDetection.setDoInput ( true );
              connectionFaceDetection.setInstanceFollowRedirects( false );
              connectionFaceDetection.setRequestMethod( "POST" );
              connectionFaceDetection.setRequestProperty( "Content-Type", "application/x-www-form-urlencoded");
              connectionFaceDetection.setRequestProperty("X-Mashape-Key", "pzFbNRvNM4mshgWJvvdw0wpLp5N1p1X3AX9jsnOhjDUkn5Lvrp");
              connectionFaceDetection.setRequestProperty( "charset", "utf-8");
              connectionFaceDetection.setRequestProperty("Accept", "application/json");
              connectionFaceDetection.setRequestProperty( "Content-Length", Integer.toString( postDataLength ));
              connectionFaceDetection.setUseCaches( false );

              DataOutputStream outputStreamForFaceDetection = new DataOutputStream( connectionFaceDetection.getOutputStream()) ;
              outputStreamForFaceDetection.write( postData );

              BufferedReader inputStreamForFaceDetection = new BufferedReader(new InputStreamReader((connectionFaceDetection.getInputStream()))) ;

              StringBuilder responseForFaceDetection  = new StringBuilder();

              while((responseStringForFaceDetection = inputStreamForFaceDetection.readLine()) != null){
                responseForFaceDetection.append(responseStringForFaceDetection);
              }

              //closing all the connections
              inputStreamForFaceDetection.close();
              outputStreamForFaceDetection.close();
              connectionFaceDetection.disconnect();

              responseStringForFaceDetection = responseForFaceDetection.toString();

         } catch(Exception e){
          LOG.warning(e.getMessage());
         }


       //########################################## API CODE SECTION  END###########################################

         //Processing the response generated by the API
         int numberOfPersonsInTheScene = 0;
         String finalresponseForCard =null;
         try{
         JSONObject responseJSONObjectForFaceDetection = new JSONObject(responseStringForFaceDetection);
         JSONArray imageFacesArray = responseJSONObjectForFaceDetection.getJSONArray("face_detection");

        //######################################### Question Processing section ######################################

         //define a function which takes input as the Map and the input string array.
         //this function would return a hashmap with detected properties and keywords from the sentence
         Map<String,String> mapForPropertiesDetected =detectProperty(propertyMapForFaceDetection,questionStringArray);
        //now we have detected the properties from the question, next step would be to traverse the response and search for respective properties.


         imageFacesArray =  filterResponse(mapForPropertiesDetected,imageFacesArray);

         LOG.info(imageFacesArray.length()+"this is the number of enteries returned");
         numberOfPersonsInTheScene =  imageFacesArray.length();

         //process on forming the specific answer only if the returned results are not null
         if(numberOfPersonsInTheScene >0){
             if (numberOfPersonsInTheScene > 1){
            	 finalresponseForCard = "There are "+numberOfPersonsInTheScene+" people in front of you. ";
            	 if((questionStringArray[0].equalsIgnoreCase("how") && mapForPropertiesDetected.containsValue(questionStringArray[1]))){
            		 finalresponseForCard += removeAdditionalProperties(imageFacesArray,detectKey(mapForPropertiesDetected,questionStringArray[1]));
            	 }
            	 else if(questionString.toLowerCase().contains("what is the")&& mapForPropertiesDetected.containsValue(questionStringArray[3])){ //condition for what is type of question
            		 finalresponseForCard +=removeAdditionalProperties(imageFacesArray, detectKey(mapForPropertiesDetected,questionStringArray[3]));
            	 }
            	 else if(!questionString.toLowerCase().contains("how many")){
            		 LOG.info("came to the last section of the if else"+finalresponseForCard);
            		 finalresponseForCard += processResponse(imageFacesArray);
            		 LOG.info(finalresponseForCard+"after exiting the function");
            	 }
             } else if(numberOfPersonsInTheScene == 1){
            	 finalresponseForCard = "There is "+numberOfPersonsInTheScene+" person in front of you. "; //how many xyz types of quesions
            	 if((questionStringArray[0].equalsIgnoreCase("how") && mapForPropertiesDetected.containsValue(questionStringArray[1]))){
            		 finalresponseForCard += removeAdditionalProperties(imageFacesArray,detectKey(mapForPropertiesDetected,questionStringArray[1]));
            	 }
            	 else if(questionString.toLowerCase().contains("what is the")&& mapForPropertiesDetected.containsValue(questionStringArray[3])){ //condition for what is type of question
            		 finalresponseForCard +=removeAdditionalProperties(imageFacesArray, detectKey(mapForPropertiesDetected,questionStringArray[3]));
            	 }
            	 else if(!questionString.toLowerCase().contains("how many")) //rest type of questions
            	 finalresponseForCard += processResponse(imageFacesArray);
             }   else finalresponseForCard = "There is no one around you that match the description in of your question OR I failed to detect";

         }
         else finalresponseForCard = "There is no one around you that match the description in of your question OR I failed to detect";
        //########################################### Question Processing ends #######################################




         } catch( JSONException e) {
             e.printStackTrace();
           }


         TimelineItem responseCardForSDKAlchemyAPI = new TimelineItem();

         responseCardForSDKAlchemyAPI.setText(finalresponseForCard);
         responseCardForSDKAlchemyAPI.setMenuItems(Lists.newArrayList(
                 new MenuItem().setAction("READ_ALOUD")));
         responseCardForSDKAlchemyAPI.setSpeakableText(finalresponseForCard);
         responseCardForSDKAlchemyAPI.setSpeakableType("Results are as follows");
         responseCardForSDKAlchemyAPI.setNotification(new NotificationConfig().setLevel("DEFAULT"));
         mirrorClient.timeline().insert(responseCardForSDKAlchemyAPI).execute()  ;
         LOG.info("New card added to the timeline");




      } else if (notification.getUserActions().contains(new UserAction().setType("LAUNCH"))) {
        LOG.info("It was a note taken with the 'take a note' voice command. Processing it.");

        // Grab the spoken text from the timeline card and update the card with
        // an HTML response (deleting the text as well).
        String noteText = timelineItem.getText();
        String utterance = CAT_UTTERANCES[new Random().nextInt(CAT_UTTERANCES.length)];

        timelineItem.setText(null);
        timelineItem.setHtml(makeHtmlForCard("<p class='text-auto-size'>"
            + "Oh, did you say " + noteText + "? " + utterance + "</p>"));
        timelineItem.setMenuItems(Lists.newArrayList(
            new MenuItem().setAction("DELETE")));

        mirrorClient.timeline().update(timelineItem.getId(), timelineItem).execute();
      } else {
        LOG.warning("I don't know what to do with this notification, so I'm ignoring it.");
      }
    }
  }

  public static String processResponse(JSONArray imageFacesArray) {


	  	String theProcessedResponse = "";
		String gender="";
		String heshe = "";

		LOG.info("inside process response");

		for(int indexPositionInJSONArray = 0; indexPositionInJSONArray < imageFacesArray.length();indexPositionInJSONArray++){
			JSONObject singleObjectInsideJSONArray = (JSONObject) imageFacesArray.get(indexPositionInJSONArray);
			LOG.info("inside the loop"+indexPositionInJSONArray);
			if(indexPositionInJSONArray == 0 && imageFacesArray.length() > 1)
				theProcessedResponse = " The first person from your left is a ";
			else if(indexPositionInJSONArray == 0 && imageFacesArray.length() == 1)
				theProcessedResponse = " The person is a ";
			else theProcessedResponse += "The next person to the right is a ";


			//gender
			if(singleObjectInsideJSONArray.getInt("sex")==0){
				gender ="female";
				heshe= "she";
			}else{
				gender= "male";
				heshe="he";
			}
			theProcessedResponse += gender +" ";

		    //age
			theProcessedResponse += ", "+singleObjectInsideJSONArray.getInt("age")+" years old. ";



			 //emotions
			 JSONObject emotionsObject = singleObjectInsideJSONArray.getJSONObject("emotion");
			 Iterator<?> keysForEmtionsObject = emotionsObject.keys();
			 int compareValue =0;
			 String accurateEmotion ="";
			 //find the most accurate emotion
			 while(keysForEmtionsObject.hasNext()){
				 double  emotionsObjectValue ;
				 String tempStorageForEmotionKey = (String) keysForEmtionsObject.next();
				 //If an emotion has value 1, then the value type is Integer else the value type is double
				 if(emotionsObject.get(tempStorageForEmotionKey) instanceof Integer){
					   emotionsObjectValue = (Integer) emotionsObject.get(tempStorageForEmotionKey)*100;
				 } else
					  emotionsObjectValue = (Double) emotionsObject.get(tempStorageForEmotionKey)*100;
				 if (emotionsObjectValue>compareValue){
					 compareValue = (int) emotionsObjectValue;
					 accurateEmotion =tempStorageForEmotionKey;

				 }
			 }

			 theProcessedResponse += "The person seems "+accurateEmotion+ " emotionally ";

			 //distance
			 JSONObject boundingBoxObject = singleObjectInsideJSONArray.getJSONObject("boundingbox");
			 JSONObject sizeOfBoundingBoxObject = boundingBoxObject.getJSONObject("size");
			 theProcessedResponse += "and "+heshe+" is "+findTheDistanceFromUser(sizeOfBoundingBoxObject.getInt("width"))+ " meters away from you." ;

			 double mouth_open;
			  if(singleObjectInsideJSONArray.get("mouth_open_wide") instanceof Integer)
				   mouth_open= singleObjectInsideJSONArray.getInt("mouth_open_wide");
			  else
				  mouth_open = singleObjectInsideJSONArray.getDouble("mouth_open_wide");
			  if(mouth_open*100>40)
			  theProcessedResponse += "There is a possibility that the person may be having a conversation";
		}


		LOG.info("The processed response is as follows"+theProcessedResponse);

		return theProcessedResponse;

}

private static float findTheDistanceFromUser(int widthInPixels) {
	//The focal length calculate in the camera is 1875
	//The average face width is considered as 14 cm
	//The function returns distance in meters.

	float distanceFromUser = (1875*14)/widthInPixels;

	return distanceFromUser/100; //Divinding by 100 to convert centimeters to meters
}

/**
   * Wraps some HTML content in article/section tags and adds a footer
   * identifying the card as originating from the Java Quick Start.
   *
   * @param content the HTML content to wrap
   * @return the wrapped HTML content
   */
  private static String makeHtmlForCard(String content) {
    return "<article class='auto-paginate'>" + content
        + "<footer><p>Java Quick Start</p></footer></article>";
  }



  public static InputStream downloadAttachment(Mirror service, String itemId, Attachment attachment) {
	    try {
	      HttpResponse resp =
	          service.getRequestFactory().buildGetRequest(new GenericUrl(attachment.getContentUrl()))
	          .execute();

	      return resp.getContent();
	    } catch (IOException e) {
	      // An error occurred.
	      LOG.warning(e.getMessage()+"This has failed");
	      return null;
	    }
	  }

  public static void printMap(Map<String,List<String>> hashmapToBePrinted)
  {
  	  //System.out.println("Fetching Keys and corresponding and Values n");

  	    for (Map.Entry<String, List<String>> entry : hashmapToBePrinted.entrySet()) {
  	            String key = entry.getKey();
  	            List<String> values = entry.getValue();
  	           // System.out.println("Key = " + key);
  	            //System.out.println("Values = " + values + "\n");
  	        }
  }

  public static Map detectProperty(Map<String,List<String>> hashmapForPorpetyToBeDetected,String[] questionStringArray )
  {
      int indexOfWord = 0;
      //Storing the detected properties inside a Hashmap
      Map <String, String> propertiesDetected= new HashMap <String,String>();
      while(indexOfWord<questionStringArray.length){
            for (Map.Entry<String,List<String>> entries : hashmapForPorpetyToBeDetected.entrySet()) {
                  	for(String singleValueInsideList : entries.getValue()){
                  		//the word in the question should be lengthier than 2 alphabets.
                  		if(singleValueInsideList.contains(questionStringArray[indexOfWord])&& questionStringArray[indexOfWord].length() >2
                  				&& isNotStopWord(questionStringArray[indexOfWord])){
                  			//System.out.println(entries.getKey() + " contains " + questionStringArray[indexOfWord]);
                  			if(!propertiesDetected.containsKey(entries.getKey()))  //if property already exist do not add or replace the entry in the hashmap
                  				propertiesDetected.put(entries.getKey(),questionStringArray[indexOfWord]);
                  	}
                  }
              }

      indexOfWord++; //proceeding to next word
      }

      return propertiesDetected;
  }

  public static boolean isNotStopWord(String wordFromTheQuestion){
	  List<String> stopWordsList = new ArrayList<String>();
	  stopWordsList.add("me");
	  stopWordsList.add("you");
	  stopWordsList.add("us");
	  if(stopWordsList.contains(wordFromTheQuestion))
		  return false;
	  else
		  return true;
  }


  //this function will search for properties within the JSON response
  public static JSONArray filterResponse(Map<String, String> mapForPropertiesDetected, JSONArray detectedFacesArray) throws JSONException{
  	JSONArray resultantSet = new JSONArray(detectedFacesArray.toString()); //make a copy of the detectedFacesArray

  	//for each property detected in the HashMap, check the resultantSet.
  	for (Map.Entry<String,String> entries : mapForPropertiesDetected.entrySet()) {
                  //System.out.println(entries.getKey() + " contains " + entries.getValue());

                  switch(entries.getKey()){
                  	case "gender" :    //LOG.info("Before entering the gender function"+resultantSet.toString());
                  					   resultantSet = genderEligibility(entries.getValue(), resultantSet);
                  					   //LOG.info("After entering the gender function"+resultantSet.toString());
                                       break;
                      case "age" : 	   //LOG.info("Before entering the age function"+resultantSet.toString());
                      				   resultantSet = ageEligibility(entries.getValue(), resultantSet);
                      				   //LOG.info("After entering the age function"+resultantSet.toString());
                      				   break;
                      case "emotion" : //LOG.info("Before entering the emotion function"+resultantSet.toString());
                                       resultantSet = emotionEligibility(entries.getValue(), resultantSet);
                                       //LOG.info("After entering the emotion function"+resultantSet.toString());
                                       break;
                      case "conversation" : //LOG.info("Before entering the conversation function"+resultantSet.toString());
									   resultantSet = conversationEligibility(entries.getValue(), resultantSet);
									   //LOG.info("After entering the conversation function"+resultantSet.toString());
						               break;
                      case "distance" ://LOG.info("Before entering the distance function"+resultantSet.toString());
				    				   resultantSet = distanceEligibility(entries.getValue(), resultantSet);
				    				   //LOG.info("After entering the distace function"+resultantSet.toString());
				                       break;
                      case "sleeping" ://LOG.info("Before entering the sleeping function"+resultantSet.toString());
  									   resultantSet = sleepingEligibility(entries.getValue(), resultantSet);
  									   //LOG.info("After entering the sleeping function"+resultantSet.toString());
                                       break;

                  }

              }
  	return resultantSet;
  }


  public static JSONArray ageEligibility (String valueFromTheQuestion, JSONArray resultantSet) throws JSONException{
  	if(resultantSet.length()>0){
  	LOG.info("before being converted"+valueFromTheQuestion);
  	valueFromTheQuestion = convertToKeyword(valueFromTheQuestion);
  	LOG.info("after being converted"+valueFromTheQuestion);
      switch(valueFromTheQuestion){
          case "kids": //remove adults from the set
          			for(int indexPositionInJSONArray = 0; indexPositionInJSONArray < resultantSet.length();indexPositionInJSONArray++){
							JSONObject singleObjectInsideJSONArray = (JSONObject) resultantSet.get(indexPositionInJSONArray);
          				if(singleObjectInsideJSONArray.getInt("age") >=18)
          					{resultantSet.remove(indexPositionInJSONArray);
          					indexPositionInJSONArray--; //change the index after deleting the element
          					}

          			}
                      break;
          case "adults": //remove the kids from the set
			            	for(int indexPositionInJSONArray = 0; indexPositionInJSONArray < resultantSet.length();indexPositionInJSONArray++){
								JSONObject singleObjectInsideJSONArray = (JSONObject) resultantSet.get(indexPositionInJSONArray);
			    				if(singleObjectInsideJSONArray.getInt("age") <18)
				    				{resultantSet.remove(indexPositionInJSONArray);
	            					indexPositionInJSONArray--;//change the index after deleting the element
	            					}
			    			}
                      break;
          case "young": //remove the people above 50 from the set
			            	for(int indexPositionInJSONArray = 0; indexPositionInJSONArray < resultantSet.length();indexPositionInJSONArray++){
								JSONObject singleObjectInsideJSONArray = (JSONObject) resultantSet.get(indexPositionInJSONArray);
			    				if(singleObjectInsideJSONArray.getInt("age") >=50)
				    				{resultantSet.remove(indexPositionInJSONArray);
	            					indexPositionInJSONArray--;//change the index after deleting the element
	            					}
			    			}
			            	 break;
//          case "old": //remove the people below 50 from the set
//		            	for(int indexPositionInJSONArray = 0; indexPositionInJSONArray < resultantSet.length();indexPositionInJSONArray++){
//							JSONObject singleObjectInsideJSONArray = (JSONObject) resultantSet.get(indexPositionInJSONArray);
//		    				if(singleObjectInsideJSONArray.getInt("age") <50 )
//			    				{resultantSet.remove(indexPositionInJSONArray);
//	        					indexPositionInJSONArray--;//change the index after deleting the element
//	        					}
//		    			}
//		            break;
      }
   }
      return resultantSet;
  }


  public static JSONArray genderEligibility (String valueFromTheQuestion, JSONArray resultantSet) throws JSONException{
   if(resultantSet.length()>0){
  	LOG.info("before being converted"+valueFromTheQuestion);
  	valueFromTheQuestion = convertToKeyword(valueFromTheQuestion);
  	LOG.info("after being converted"+valueFromTheQuestion);
  	switch(valueFromTheQuestion){
          case "males": //remove adults from the set
          			for(int indexPositionInJSONArray = 0; indexPositionInJSONArray < resultantSet.length();indexPositionInJSONArray++){
							JSONObject singleObjectInsideJSONArray = (JSONObject) resultantSet.get(indexPositionInJSONArray);
          				if(singleObjectInsideJSONArray.getInt("sex") ==0)
          					{resultantSet.remove(indexPositionInJSONArray);
          					indexPositionInJSONArray--; //change the index after deleting the element
          					}

          			}
                      break;
          case "females": //remove adults from the set
  			for(int indexPositionInJSONArray = 0; indexPositionInJSONArray < resultantSet.length();indexPositionInJSONArray++){
					JSONObject singleObjectInsideJSONArray = (JSONObject) resultantSet.get(indexPositionInJSONArray);
  				if(singleObjectInsideJSONArray.getInt("sex") !=0)
  					{resultantSet.remove(indexPositionInJSONArray);
  					indexPositionInJSONArray--; //change the index after deleting the element
  					}

  			}
              break;
      }
   }
      return resultantSet;
  }

   public static JSONArray conversationEligibility (String valueFromTheQuestion, JSONArray resultantSet) throws JSONException{
    if(resultantSet.length()>0){
  	LOG.info("before being converted"+valueFromTheQuestion);
      valueFromTheQuestion = convertToKeyword(valueFromTheQuestion);
      LOG.info("after being converted"+valueFromTheQuestion);
      switch(valueFromTheQuestion){
          case "conversation": //remove adults from the set
                      for(int indexPositionInJSONArray = 0; indexPositionInJSONArray < resultantSet.length();indexPositionInJSONArray++){
                          JSONObject singleObjectInsideJSONArray = (JSONObject) resultantSet.get(indexPositionInJSONArray);
                          if(!isMouthOpen(singleObjectInsideJSONArray))
                              {resultantSet.remove(indexPositionInJSONArray);
                              indexPositionInJSONArray--; //change the index after deleting the element
                              }

                      }
                      break;
      }
    }
      return resultantSet;
  }

   //isMouthOpen checks if a person is having a conversation by checking if the mouth of the person is possibly open
  public static boolean isMouthOpen(JSONObject singleObjectInsideJSONArray) throws JSONException{
      double mouth_open;
                if(singleObjectInsideJSONArray.get("mouth_open_wide") instanceof Integer)
                     mouth_open= singleObjectInsideJSONArray.getInt("mouth_open_wide");
                else
                    mouth_open = singleObjectInsideJSONArray.getDouble("mouth_open_wide");

                if(mouth_open*100>50)
                    return true;
                else
                  return false;

  }

  //This function returns all the user satisfying the respective sleeping eligibility
  public static JSONArray sleepingEligibility (String valueFromTheQuestion, JSONArray resultantSet) throws JSONException{
   if(resultantSet.length()>0){
  	LOG.info("before being converted"+valueFromTheQuestion);
      valueFromTheQuestion = convertToKeyword(valueFromTheQuestion);
      LOG.info("after being converted"+valueFromTheQuestion);
      switch(valueFromTheQuestion){
          case "sleeping": //remove adults from the set
                      for(int indexPositionInJSONArray = 0; indexPositionInJSONArray < resultantSet.length();indexPositionInJSONArray++){
                          JSONObject singleObjectInsideJSONArray = (JSONObject) resultantSet.get(indexPositionInJSONArray);
                          if(!isSleeping(singleObjectInsideJSONArray))
                              {resultantSet.remove(indexPositionInJSONArray);
                              indexPositionInJSONArray--; //change the index after deleting the element
                              }

                      }
                      break;
          case "awake": //remove adults from the set
              for(int indexPositionInJSONArray = 0; indexPositionInJSONArray < resultantSet.length();indexPositionInJSONArray++){
                  JSONObject singleObjectInsideJSONArray = (JSONObject) resultantSet.get(indexPositionInJSONArray);
                  if(isSleeping(singleObjectInsideJSONArray))
                      {resultantSet.remove(indexPositionInJSONArray);
                      indexPositionInJSONArray--; //change the index after deleting the element
                      }

              }
              break;
      }
   }
      return resultantSet;
  }

  //This function returns true if the key eye_closed is greater than 60
  public static boolean isSleeping(JSONObject singleObjectInsideJSONArray) throws JSONException{
      double eye_closed;
                if(singleObjectInsideJSONArray.get("eye_closed") instanceof Integer)
              	  eye_closed= singleObjectInsideJSONArray.getInt("eye_closed");
                else
              	  eye_closed = singleObjectInsideJSONArray.getDouble("eye_closed");

                if(eye_closed*100>60)
                    return true;
                else
                  return false;

  }

  //returns all users that satisfied the distance eligibility
  public static JSONArray distanceEligibility (String valueFromTheQuestion, JSONArray resultantSet) throws JSONException{

  	if(resultantSet.length()>0){
  	LOG.info("before being converted"+valueFromTheQuestion);
      valueFromTheQuestion = convertToKeyword(valueFromTheQuestion);
      JSONArray closestPersonArray = new JSONArray();
      LOG.info("after being converted"+valueFromTheQuestion);
      switch(valueFromTheQuestion){
          case "closest": //remove adults from the set
          			float minimumDistance = 9999;
                      float distanceOfCurrentObject;
                      int closestPersonIndex = 0;
                      for(int indexPositionInJSONArray = 0; indexPositionInJSONArray < resultantSet.length();indexPositionInJSONArray++){
                          JSONObject singleObjectInsideJSONArray = (JSONObject) resultantSet.get(indexPositionInJSONArray);

                          JSONObject boundingBoxObject = singleObjectInsideJSONArray.getJSONObject("boundingbox");
                          JSONObject sizeOfBoundingBoxObject = boundingBoxObject.getJSONObject("size");
                          distanceOfCurrentObject =findTheDistanceFromUser(sizeOfBoundingBoxObject.getInt("width"));
                              if(distanceOfCurrentObject < minimumDistance){
                                  minimumDistance = distanceOfCurrentObject;
                                  closestPersonIndex = indexPositionInJSONArray;
                              }
                      }
                      closestPersonArray.put(resultantSet.get(closestPersonIndex));
                      break;
           }
          return (closestPersonArray.length()>0)?closestPersonArray:resultantSet;
  	  }

		 return resultantSet;

      }


  public static JSONArray emotionEligibility (String valueFromTheQuestion, JSONArray resultantSet) throws JSONException{
   if(resultantSet.length()>0){
  	  LOG.info("before being converted"+valueFromTheQuestion);
      valueFromTheQuestion = convertToKeyword(valueFromTheQuestion);
      LOG.info("after being converted"+valueFromTheQuestion);
      switch(valueFromTheQuestion){
          case "happy": //remove adults from the set
                      for(int indexPositionInJSONArray = 0; indexPositionInJSONArray < resultantSet.length();indexPositionInJSONArray++){
                          JSONObject singleObjectInsideJSONArray = (JSONObject) resultantSet.get(indexPositionInJSONArray);
                          if(!"happy".equals(appropriateEmotion(singleObjectInsideJSONArray)))
                              {resultantSet.remove(indexPositionInJSONArray);
                              indexPositionInJSONArray--; //change the index after deleting the element
                              }

                      }
                      break;
          case "sad": //remove adults from the set
                      for(int indexPositionInJSONArray = 0; indexPositionInJSONArray < resultantSet.length();indexPositionInJSONArray++){
                          JSONObject singleObjectInsideJSONArray = (JSONObject) resultantSet.get(indexPositionInJSONArray);
                          if(!"sad".equals(appropriateEmotion(singleObjectInsideJSONArray)))
                              {resultantSet.remove(indexPositionInJSONArray);
                              indexPositionInJSONArray--; //change the index after deleting the element
                              }

                      }
                      break;
          case "angry": //remove adults from the set
                      for(int indexPositionInJSONArray = 0; indexPositionInJSONArray < resultantSet.length();indexPositionInJSONArray++){
                          JSONObject singleObjectInsideJSONArray = (JSONObject) resultantSet.get(indexPositionInJSONArray);
                          if(!"angry".equals(appropriateEmotion(singleObjectInsideJSONArray)))
                              {resultantSet.remove(indexPositionInJSONArray);
                              indexPositionInJSONArray--; //change the index after deleting the element
                              }

                      }
                      break;
          case "calm": //remove adults from the set
              for(int indexPositionInJSONArray = 0; indexPositionInJSONArray < resultantSet.length();indexPositionInJSONArray++){
                  JSONObject singleObjectInsideJSONArray = (JSONObject) resultantSet.get(indexPositionInJSONArray);
                  if(!"calm".equals(appropriateEmotion(singleObjectInsideJSONArray)))
                      {resultantSet.remove(indexPositionInJSONArray);
                      indexPositionInJSONArray--; //change the index after deleting the element
                      }

              }
              break;

          case "disgust": //remove adults from the set
              for(int indexPositionInJSONArray = 0; indexPositionInJSONArray < resultantSet.length();indexPositionInJSONArray++){
                  JSONObject singleObjectInsideJSONArray = (JSONObject) resultantSet.get(indexPositionInJSONArray);
                  if(!"disgust".equals(appropriateEmotion(singleObjectInsideJSONArray)))
                      {resultantSet.remove(indexPositionInJSONArray);
                      indexPositionInJSONArray--; //change the index after deleting the element
                      }

              }
              break;
      }
   }
      return resultantSet;
  }


      //This function returns the best emotion of the respective user from the array of emotions that represent the user.
  public static String appropriateEmotion(JSONObject singleObjectInsideJSONArray) throws JSONException{
       JSONObject emotionsObject = singleObjectInsideJSONArray.getJSONObject("emotion");
               Iterator<?> keysForEmtionsObject = emotionsObject.keys();
               int compareValue =0;
               String accurateEmotion ="";
               //find the most accurate emotion
               while(keysForEmtionsObject.hasNext()){
                   double  emotionsObjectValue ;
                   String tempStorageForEmotionKey = (String) keysForEmtionsObject.next();
                   //If an emotion has value 1, then the value type is Integer else the value type is double
                   if(emotionsObject.get(tempStorageForEmotionKey) instanceof Integer){
                         emotionsObjectValue = (int) emotionsObject.get(tempStorageForEmotionKey)*100;
                   } else
                        emotionsObjectValue = (double) emotionsObject.get(tempStorageForEmotionKey)*100;
                   if (emotionsObjectValue>compareValue){
                       compareValue = (int) emotionsObjectValue;
                       accurateEmotion =tempStorageForEmotionKey;

                   }
               }

    return accurateEmotion;
  }


  //This function acts as a knowledge base for all the plural strings. If a singular string occurs in the question
  // it is then replaced by the plural string to work with the code.
  public static String convertToKeyword(String nonKeyWordString){

  	switch(nonKeyWordString){
  	case "kid":
  	case "child": nonKeyWordString = "kids";
  				break;
  	case "adult": nonKeyWordString = "adults";
  				break;
      //possibility of words for men
  	case "man" :
  	case "boy" :
  	case "boys":
  	case "males" :
  	case "men" : nonKeyWordString = "males";
					break;
      //possibility of words for women
  	case "lady" :
  	case "ladies":
  	case "girl" :
  	case "girls":
  	case "woman":
  	case "women" :
  	case "females" : nonKeyWordString ="females";
  				break;
    //possibility of words for conversation
    case "conversing" :
    case "talking":
    case "chatting":
    case "speaking": nonKeyWordString = "conversation";
     			break;

    //emotions
    case "safe" : nonKeyWordString ="angry";
    case "disgusted": nonKeyWordString ="disgust";

    //distance
    case "nearest":
    case "close" : nonKeyWordString="closest";



  	}
  	return nonKeyWordString; //return the converted string
  }

  //This function detects the property word matching to the string in question from hashmap
	private static String detectKey(
			Map<String, String> mapForPropertiesDetected, String stringToBeMatched) {

		for (Entry<String, String> entry : mapForPropertiesDetected.entrySet()) {
            if (entry.getValue().equals(stringToBeMatched)) {
                return entry.getKey();
            }
        }
		return null;
	}


	private static String removeAdditionalProperties(JSONArray imageFacesArray,
			String detectedKey) throws JSONException {
		String filterResponse = null;
		String gender,heshe;
		for(int indexPositionInJSONArray = 0; indexPositionInJSONArray < imageFacesArray.length();indexPositionInJSONArray++){
			JSONObject singleObjectInsideJSONArray = (JSONObject) imageFacesArray.get(indexPositionInJSONArray);

			if(indexPositionInJSONArray == 0 && imageFacesArray.length() > 1)
				filterResponse = " The first person from your left is ";
			else if(indexPositionInJSONArray == 0 && imageFacesArray.length() == 1)
				filterResponse = " The person is a";
			else filterResponse += "The next person to their right is ";


			if(singleObjectInsideJSONArray.getInt("sex")==0){
				gender ="female";
				heshe= "she";
			}else{
				gender= "male";
				heshe="he";
			}

            if(detectedKey.equalsIgnoreCase("gender"))
                filterResponse +="a "+gender;

			//age
			if(detectedKey.equalsIgnoreCase("age"))
			filterResponse += ""+singleObjectInsideJSONArray.getInt("age")+" years old. ";

			//emotion
            if(detectedKey.equalsIgnoreCase("emotion"))
            filterResponse += ""+appropriateEmotion(singleObjectInsideJSONArray)+ " emotionally ";


			 //distance
			if(detectedKey.equalsIgnoreCase("distance")){
		    JSONObject boundingBoxObject = singleObjectInsideJSONArray.getJSONObject("boundingbox");
			JSONObject sizeOfBoundingBoxObject = boundingBoxObject.getJSONObject("size");
			filterResponse += findTheDistanceFromUser(sizeOfBoundingBoxObject.getInt("width"))+ " meters away from you." ;
			}

		}

		LOG.info("This is the filtered response"+filterResponse);

		return filterResponse;
	}




}
