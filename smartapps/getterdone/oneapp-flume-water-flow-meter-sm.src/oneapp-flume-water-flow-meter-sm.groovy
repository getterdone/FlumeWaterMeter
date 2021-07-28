/**
 *  OneApp Flume Water Flow Meter SM
 *  Smart App/ Service Manager for Flume Water Flow Meter
 *  This will create a companion Device Handler for the Flume device
 *  Version 2.0 OneApp
 *
 *  You MUST enter the API Key value via 'App Settings'->'Settings'->'api_key' in IDE by editing this SmartApp code
 *  This key is provided by Flume upon request
 *
 *  Copyright 2021 Bruce Andrews, Ulices Soriano
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */

import java.util.TimeZone
import java.text.SimpleDateFormat
import groovy.json.*
import groovy.time.*
import groovy.time.TimeCategory
 
definition(
    name: "OneApp Flume Water Flow Meter SM",
    namespace: "getterdone",
    author: "Ulices Soriano",
    description: "Service Manager for cloud-based API for Flume Water Flow meter",
    category: "My Apps",
    iconUrl: "https://raw.githubusercontent.com/getterdone/FlumeWaterMeter/master/Icons/water-faucet128.png",
    iconX2Url: "https://raw.githubusercontent.com/getterdone/FlumeWaterMeter/master/Icons/water-faucet256.png",
    iconX3Url: "https://raw.githubusercontent.com/getterdone/FlumeWaterMeter/master/Icons/water-faucet256.png",
 	singleInstance: true) 
	
	
{
    appSetting "FlumeAPI_Key"
    appSetting "FlumeAPI_Secret"
}


preferences {
	page(name: "pageOne", title: "Options", uninstall: true, install: true) {
		section("Inputs") {
        		paragraph ("You MUST set the 'API Key' and 'API Secret' via App Settings in IDE")
            		label (title: "Assign a name for Service Manager", required: true, multiple: true)
							input(
					name: "username",
					type: "email",
					required: true,
					title: "Email Address"
			)
			input(
					name: "password",
					type: "password",
					required: true,
					title: "Password"
			)
            
        
            
            		/* input (name: "Flume_awayModes", type: "mode", title: "Enter SmartThings modes when water meter should be Away", 
                    		multiple: true, required: false) */
            		/* input (name: "userFlume_locName", type: "text", title: "Enter Flume location name assigned to Flume flow meter", 
                    		multiple: false, required: true) */
                    input (name: "configLoggingLevelIDE",
                        title: "IDE Live Logging Level:",   //\n Messages with this level and higher will be logged to the IDE.",
                        type: "enum",
                        options: [
                            "0" : "None",
                            "1" : "Error",
                            "2" : "Warn",
                            "3" : "Info",
                            "4" : "Debug",
                            "5" : "Trace"
                        ],
                        defaultValue: "3",
                        displayDuringSetup: true,
                        required: false
                    )
					
		}
            section("") {
        		href(name: "hrefNotRequired", description: "Tap to donate via PayPal", required: false, style: "external", image: "https://raw.githubusercontent.com/getterdone/FlumeWaterMeter/master/Icons/paypal-logo-png-2120.png",
                	 url: "https://www.paypal.com/donate/?cmd=_donations&business=ST5DKP85282KA&currency_code=USD&source=url", title: "Your donation is appreciated!" )
    		}
            
	}
}

def getApiBase() { return "https://api.flumetech.com/" }

private String FlumeAPIKey() {return appSettings.FlumeAPI_Key}
private String FlumeAPISecret() {return appSettings.FlumeAPI_Secret}

//required methods
def installed() {
	//log.debug "Flume SM installed with settings: ${settings}"
   /*  state.enteredLocName =  userFlume_locName  */ //save off the location name entered by user
    runIn(3, "initialize")
}

def updated() {


    if ( true ){ //state.enteredLocName != userFlume_locName) { //if location name changed, need to make a new device
    	logger("Flume SM updated() called- new device with settings: ${settings}","trace")
        unsubscribe()
        cleanup()
    	runIn(10, "initialize") //deleteChildDevice seems to take a while to delete; wait before re-creating
    } else {
	   	state.loggingLevelIDE = (settings.configLoggingLevelIDE) ? settings.configLoggingLevelIDE.toInteger() : 3
		logger("Flume SM updated() called- same name, no new device with settings: ${settings}","info")
    }
   


}

def initialize() {
    state.loggingLevelIDE = (settings.configLoggingLevelIDE) ? settings.configLoggingLevelIDE.toInteger() : 3
    logger("Flume SM initialize() called with settings: ${settings}","trace")
	// get the value of api key
	def mySecret = FlumeAPISecret()  //appSettings.api_key
    if (mySecret.length() <20) {
    	logger("Flume SM initialize- api_secret value not set properly in IDE: ${mySecret}","error")
    }
    
    state.lastSuccessfulCall = null
    
    state.initializeDelay = false
    state.currentDeviceIndex = null
    
    state.messageRead = false
    state.firstDevice = null
    state.secondDevice = null
    
    state.delayPollData = false
	state.delayFlowData = false
	state.delaySyncData = false

    state.lastPolledSuccess = false
    state.lastSyncSuccess = false
    state.lastFlowDataSuccess = false
    
    state.myLocations = []
    state.myQueryData = []
    state.myAlertData = []
    
    state.deviceBattery = null
    state.bridgeConnection = null
    state.sameTZ = true
    state.myTZ = null
    
    state.sameBatteryLevel = true
    state.myBatterLevel = null
    
    state.sameConnectedStatus = true
    state.myConnectedSatus = null
    
	state.flumeUserId = getflumeUserId()
   	 
     initFlume_locations(flumeUserId)
     def data = []
     def Flume_Devices = getChildDevices()
     log.debug("line 160 Flume_Devices '${Flume_Devices}'")
    Flume_Devices?.each {
    	//if (it.deviceNetworkId == deviceid) {
    
	state.flumeDeviceId  = it.deviceNetworkId //getflumeDeviceId()
    state.Flume_location = null
    state.childDevice = null
    state.inAlert = false
    
    /*
    runEvery5Minutes(startTimerAlerts)
    //runEvery5Minutes(pollSLAlert)
   	
    runEvery30Minutes(myRandomSyncLocationData) //syncLocationData)
    
    runEvery10Minutes(determineAllFlows) //determineCloudFlow)
    */
    
    //gets custom water alerts, low leak alerts,
    //possibly high flow (untested: need to create high flow real life case at home to confirm)
    //every 4 minutes/hour [future enhancement? create DH method refresh/call for alerts to generate events [water:dry/wet]]
   
    schedule("0 0/4 * * * ?", startTimerAlerts)
    
    //gets water flow values every 6 minutes/hour
   // schedule("0 0/6 * * * ?", determineAllFlows)
    
    //gets battery level, bridge wifi connection status every 31 minutes/hour
    schedule("0 0/31 * * * ?", startTimerMyDataSync) //myRandomSyncLocationData)
    
    
    /* state.homeAway = "home" */
    subscribe(location, "mode"/* , modeChangeHandler */)
   //Dec 21 initFlume_locations(flumeUserId) //determine Flume location to use
    log.debug("line 172 initialize()FLOW state.Flume_location = ${state.Flume_location}")
    
    if ( state.flumeDeviceId ) { //state.Flume_location){          //state.flumeDeviceId) {   //state.Flume_location) { 
         log.debug("line 175 we have a device; put it into initial state")      
        def eventData = [name: "water", value: "dry"]
        //log.debug("state.Flume_location?.id ===${state.Flume_location?.id}")
          //def idToString = (state.Flume_location?.id).toString()
          log.debug("inside initialize state.flumeDeviceId '${state.flumeDeviceId}'")
        def existingDevice = getChildDevice(state.flumeDeviceId)//idToString)              
        existingDevice?.generateEvent(eventData) //this was off the hold time?? now back on not sure what is happening
     state.inAlert =  false
       // Nov 16 2020  
       // schedule("0 0/4 * * * ?", pollSLAlert)
      //  schedule("0 0/31 * * * ?", syncLocationData)
   //    schedule("0 0/3 * * * ?", pollSLAlert) //Poll Flume cloud for leak alert //change 0 0/3 to 4  0/4 for four minutes 0/3 Dec3
   
   		
        runIn(5,"initDevice") //update once things are initialized
    }
    
    }
    
}

def getDefaultHeaders() {
	return [
		"User-Agent": "okhttp/3.2.0",
		"Content-Type": "application/json"
	]
}

def getDefaultAuthHeaders() {
	def headers = getDefaultHeaders()
    //log.debug("Getting myToken to use in getDefaultAuthHeaders() ${state?.myToken}")
	headers["Authorization"] = "Bearer ${state?.myToken[0]}"
	return headers
}


def login() {
	def flumeUserId = null;
	def body = new groovy.json.JsonOutput().toJson([
			"grant_type":"password",
			"client_id":FlumeAPIKey(),
			"client_secret":FlumeAPISecret(),
			"username"   : settings.username,
			"password": settings.password			
	])
	def params = [
		uri: getApiBase(),
		path: "oauth/token",
		headers: getDefaultHeaders(),
		body: body
	]

	try {
		//log.debug("getJWT: Trying to login.")
		httpPostJson(params) { resp ->
			if (resp?.status == 200) {
				//log.debug("getJWT: Successful")
                //log.debug("get resp.data: ${resp.data}")
                //log.debug("getsize: ${resp.data.getAt('data').size()}")
                //  //log.debug("getAt: ${resp.data.getAt('data').access_token}")
              //  DecodedJWT jwt = JWT.decode(response.getInstance().getAccessToken())
                state.myToken = resp.data.getAt('data').access_token
                //log.debug("getToken: ${state.myToken}")
				state.jwt = resp.data.jwt
				def jsonSlurper = new JsonSlurper()
                //log.debug("getsize myToken: ${state.myToken.size()}")
                ////log.debug("getsize myToken: ${state.myToken.size()}")
				def parsedJwt = /*resp.data.jwt*/state.myToken[0].tokenize(".")[1]
				parsedJwt = new String(parsedJwt?.decodeBase64(), "UTF-8")
				parsedJwt = jsonSlurper.parseText(parsedJwt)
				flumeUserId = parsedJwt?.user_id
				state.jwtExpireTime = parsedJwt?.exp
				state.flumeUserId = flumeUserId
			} else {
				log.error("getJWT: 'Unexpected' Response: ${resp?.status}: ${resp?.data}")
			}
		}
	} catch (ex) {
		log.error("getJWT Exception:", ex)
	}
	//log.debug("myFlumeID = ${state.flumeUserId}")
	return state.flumeUserId//flumeUserId
}

def isLoggedIn() {
	if (state?.flumeUserId == null || state?.jwtExpireTime == null || state?.jwtExpireTime <= (new Date().getTime() / 1000l)) {
		return false
	}
	return true
}


def getflumeUserId() {
	if (!isLoggedIn()) {
		return login()
	}
	return state.flumeUserId
}

def getflumeDeviceId(){
	if (!isLoggedIn()) {
		return login()
	}
    log.debug("line 232 state.flumeDeviceId '${state.flumeDeviceId}'")
	return state.flumeDeviceId
}

def getmyTimeZone(){
	return state.myTZ
}

def uninstalled() {
    logger("Flume SM uninstalled() called","trace")
    cleanup()
}


//remove things
def cleanup() {
    logger("Flume SM cleanup() called","trace")
    def Flume_Devices = getChildDevices()
    Flume_Devices.each {
    	logger("Flume SM cleanup- deleting SL deviceNetworkID: ${it.deviceNetworkId}","info")
    	try {
            deleteChildDevice(it.deviceNetworkId)
        }
    	catch (e) {
    		logger("Flume SM cleanup- caught and ignored deleting child device: {it.deviceNetworkId}: $e","info")
       	}
    }
    state.myLocations = null
    state.Flume_location = null
    state.childDevice = null
    state.inAlert = false
    Flume_Devices = null
    
}

//Handler for schedule; determine if there are any alerts


def startTimerAlerts() {

   Random rnd = new Random()
   		int my1stSecond = rnd.nextInt(2)+1
    	int my2ndSecond = rnd.nextInt(8)+1
    	int firstTimeOffset = "2${my1stSecond}" as int
    	int secondTimeOffset = "3${my2ndSecond}" as int
		log.debug("line 356 ${new Date()} state.delayPollData '${state.delayPollData}'")
        def i = rnd.next(5)
		int delaySeconds = i % 2
       // runIn((delaySeconds==0) ? firstTimeOffset : secondTimeOffset , delayPoll)
 		
   // log.debug ("line 683 ${new Date()} string? ${(data instanceof String)} deviceid ${data} Map? ${(data instanceof Map)}")
     //    [data: ["deviceID": tempID,overwrite: false]]

	def seconds = (delaySeconds==0) ? firstTimeOffset : secondTimeOffset
	log.debug "in Alerts start timer"
   
    def now = new Date()
	def runTime = new Date(now.getTime() + (seconds * 1000))
	//runOnce(runTime, myTimerEventHandler)
    log.debug("line 370 pollSLAlert runTime  ${runTime}")
    runOnce(runTime, pollSLAlert, [data: ["deviceID": state.myLocations.get(0).myDeviceID,overwrite: true]] )
}


def startTimerMyDataSync() {
	def seconds = 32
	log.debug "in DataSync start timer"
   
    def now = new Date()
	def runTime = new Date(now.getTime() + (seconds * 1000))
	//runOnce(runTime, myTimerEventHandler)
    log.debug("runTime  ${runTime}")
    runOnce(runTime, syncLocationData, [data: ["deviceID": state.myLocations.get(0).myDeviceID,overwrite: true]] )
}
/*
def myTimerEventHandler() {
     //do the things that I want delayed in this function
     
     log.debug "doing the delayed things"
}
*/


def pollSLAlert(data) {

 log.debug ("line 394 ${new Date()} string? ${(data instanceof String)} deviceid ${data} Map? ${(data instanceof Map)}")
 log.debug("line 396 state.myAlertData '${state.myAlertData}'    state.myLocations  ${state.myLocations} ")
//logger("line 397 ${new Date()} Flume SM pollSLAlert() called ${deviceid}","info")

def deviceid = (data instanceof String) ? data : data.deviceID

log.debug("line 401 state.messageRead '${state.messageRead}'  deviceid ${deviceid}      state.delayPollData '${state.delayPollData}'")
		def activateWaterAlert = false
   				 def myLowWaterFlowAlert = null
   				 def myLowWaterFlowAlertMessage = []
    			def Flume_locationsAlert
    			def alertsSetData = null
                def errorRetrieve = false
            
            	
	state.Flume_location = state.myLocations.get(0).myDeviceID
    //log.debug("line 341 pollSLAlert() called ")
    //logger("Flume SM pollSLAlert() called","trace")
     //def idToString = (state.Flume_location?.id).toString()
    def existingDevice = getChildDevice(state.flumeDeviceId)//idToString)
    //state.myLocations
	if (state.Flume_location==deviceid){
     log.debug ("begin 409: state.Flume_location true" )
       def params = [
            uri:  getApiBase(),
			path: "users/${flumeUserId}/notifications",         
			headers: getDefaultAuthHeaders(),
           // query: [read: "false"],
    ]
//    if(state.messageRead==false){ //&& state.delayPollData == false){
    def nowTime= now()+30000
    def checkNotification = null
    def myDeviceCheck = state.myLocations.get(0).myDeviceID
       def deviceCounter = state.myLocations.size()
               def nameIndex = 0
                log.debug("line 422 ${myDeviceCheck}vs${deviceid} myDeviceCheck==deviceid ${myDeviceCheck==deviceid} state.myLocations size '${deviceCounter}'    '${state.myLocations}'")
                log.debug( "line 423 state.lastSuccessfulCall<nowTime  ${state.lastSuccessfulCall<nowTime}   state.lastSuccessfulCall ${state.lastSuccessfulCall}   nowTime ${nowTime} ")
               
//   if (state.lastSuccessfulCall<nowTime && state.delayPollData==false && myDeviceCheck==deviceid){
        try {
            httpGet(params) {resp ->
            
            
    			//logger("line 411 Flume SM pollSLAlert resp.data: ${resp.data}","debug")
                def resp_data = resp.data
              
           //     log.debug ("begin: pollSLAlert() resp_data line 272 begin ${resp_data} end")
               
                   
                   // def myCollection = ["apple", "apple", "banana", "banana"]
					//def myFirstSet = myCollection as Set
                   
                   resp_data.data.message.each{ tempMessage->
                   
                   
                   logger("line 449 ${new Date()} Flume SM pollSLAlert() successfully called","info")
                   
                   log.debug ("line 450 tempMessage '${tempMessage}'")
                   if(tempMessage.contains("Low Flow Leak")  || tempMessage.contains("Water") || tempMessage.contains("High Flow")){
                   myLowWaterFlowAlertMessage.add(tempMessage) //"Low Water Alert True"
                   myLowWaterFlowAlert = true
                   activateWaterAlert = true
                   Flume_locationsAlert = "Low Flow Leak"
                   			}//Low Flow Leak condition
                   
                   }// end of for eachMessage Loop
                    state.lastSuccessfulCall = now()
                  log.debug("line 461 myLowWaterFlowAlertMessage.size() '${myLowWaterFlowAlertMessage.size()}'")
                  
               	state.messageRead = true
                state.lastPolledSuccess = true
				 log.debug ("resp_data line 461 begin '${myLowWaterFlowAlertMessage}' end ")
             /*
             if (myLowWaterFlowAlert) {    
            //  if (true) {
                    //send wet event to child device handler every poll to ensure not lost due to handler pausing
    				logger("Flume SM pollSLAlert Alert0 received: ${Flume_locationsAlert}; call changeWaterToWet","info")
                    existingDevice?.changeWaterToWet()
                    state.inAlert =  true
                } else {
                    if (state.inAlert){
                        //alert removed, send dry event to child device handler only once
    					logger("Flume SM pollSLAlert Alert0 deactivated ; call changeWaterToDry","info")
                        existingDevice?.changeWaterToDry()
                        state.inAlert =  false
                    }
                }
                */
                
            }//end of resp.
         
        } catch (e) {
        	state.lastPolledSuccess=false
            state.delayPollData = true
            errorRetrieve = true
			//state.delayFlowData = false
			//state.delaySyncData = false
            //log.debug("line 492 current state: Too Many Request error ${state.myAlertData}")
    		logger("line 494 Flume SM pollSLAlert Too Many Request error ${state.myAlertData} retrieving alerts: ${deviceid} '${e}'","error")
        }
    //    }//last state.lastSuccessfulCall<nowTime 
          
         
          
          def tempDeviceID = null
          def tempLocationName = null
        if(activateWaterAlert && errorRetrieve==false){ //myLowWaterFlowAlertMessage.size()>0){
         log.debug("line 503 current successful pollSAlert call ${state.myAlertData}")
    			//def tempMessageList = []
                  log.debug("line 497 myLowWaterFlowAlertMessage ${myLowWaterFlowAlertMessage}  ")
                //  alertsSetData = myLowWaterFlowAlertMessage as Set
             
              //    tempMessageList = alertsSetData
                 deviceCounter = state.myLocations.size()
    			//def nameIndex = 0
    		//	 log.debug("line 450 alertsSetData.size() '${alertsSetData?.size()}'    alertsSetData '${alertsSetData}'  ")
     for (def i = 0; i <deviceCounter; i++) {
    tempLocationName = state.myLocations.get(i).myLocationName
     tempDeviceID= state.myLocations.get(i).myDeviceID
   //  ("line 454 alertsSetData ${alertsSetData}    tempMessageList  ${tempMessageList}")
     for(def j = 0; j < myLowWaterFlowAlertMessage.size(); j++){
     	if(myLowWaterFlowAlertMessage.get(j).contains(tempLocationName) && activateWaterAlert==true){
        log.debug("line 510 ${i} tempLocationName= ${tempLocationName}  myLowWaterFlowAlertMessage.get(j) results: ${myLowWaterFlowAlertMessage.get(j)} for deviceid ${tempDeviceID}" )

					
		def existingToWet = getChildDevice(tempDeviceID) 
		//  if (myLowWaterFlowAlert) {    
            //  if (true) {
                    //send wet event to child device handler every poll to ensure not lost due to handler pausing
    				logger("Flume SM pollSLAlert Alert0 received: ${tempLocationName}; call changeWaterToWet","info")
                   state.myAlertData.get(i).inAlertMode=true
                   //existingDevice?.changeWaterToWet(existingToWet)
                  // existingToWet.changeWaterToWet()
                  existingToWet.changeWaterToWet(existingToWet)
                  existingToWet.refreshAlert(existingToWet)
                   //existingDevice?.refreshAlert(tempDeviceID)
                   
                  //existingDevice?.changeWaterToWet()
                                                 // state.inAlert =  true
              //  } else {
               //     if (state.inAlert){
                        //alert removed, send dry event to child device handler only once
    		
               //     }
               // }
        
        }else{
        			logger("Flume SM pollSLAlert Alert0 deactivated ${tempLocationName}; call changeWaterToDry","info")
                        def existingToDry = getChildDevice(tempDeviceID) 
               	       state.myAlertData.get(i).inAlertMode=false                          //  state.inAlert =  false
        				existingDevice?.changeWaterToDry(existingToDry)
                        existingToDry.refreshAlert(existingToDry)
                      // existingToDry.changeWaterToDry()
                       //existingToDry.changeWaterToDry(existingToDry)
                        //existingDevice?.changeWaterToDry()
        }
     
     }
     
                  }
              
                  }
                //if(myLowWaterFlowAlertMessage.size()==0 && state.lastPolledSuccess==true){ //&& errorRetrieve==false){
                else{
                  if(myLowWaterFlowAlertMessage.size()==0 && errorRetrieve==false){
                  log.debug("line 553 myLowWaterFlowAlertMessage ${myLowWaterFlowAlertMessage}")
               
                deviceCounter = state.myLocations.size()
    			//def nameIndex = 0
              
    			// log.debug("line 450 alertsSetData.size() '${alertsSetData?.size()}'    alertsSetData '${alertsSetData}'  ")
     			for (def i = 0; i <deviceCounter; i++) {
               	tempDeviceID= state.myLocations.get(i).myDeviceID
                def existingToDry = getChildDevice(tempDeviceID) 
                tempLocationName = state.myAlertData.get(i).myLocationName
                 log.debug("line 552 before myLowWaterFlowAlertMessage.size()==0 Flume SM pollSLAlert Alert0 deactivated ${tempLocationName}; call changeWaterToDry state.myAlertData ${state.myAlertData}")
               
                    				 
                  state.myAlertData.get(i).inAlertMode=false
                  existingDevice?.changeWaterToDry()
                   existingToDry.refreshAlert(existingToDry)
                   log.debug("line 572 after myLowWaterFlowAlertMessage.size()==0 Flume SM pollSLAlert Alert0 deactivated ${tempLocationName}; call changeWaterToDry state.myAlertData ${state.myAlertData}")
                  //existingDevice?.changeWaterToDry(tempDeviceID)
                  
                   // def existingDevice = getChildDevice(it.deviceNetworkId)    //state.flumeDeviceId)//idToString)//idToString)
 				//	existingDevice?.refreshAlert(tempDeviceID)
                  
                  }
                  
                  }
               }  
  
     
 //   }//end of MessageRead == false
                
  } //end of state.FlumeLocations
  
  
  logger("line 581 state.flumeDeviceId = ${state.flumeDeviceId}  state.lastPolledSuccess results: ${state.lastPolledSuccess}", "info")
  
                  
   /*
  if(state.delayPollData){
  
  		
        Random rnd = new Random()
   		int my1stSecond = rnd.nextInt(4)+1
    	int my2ndSecond = rnd.nextInt(7)+1
    	int firstTimeOffset = "2${my1stSecond}" as int
    	int secondTimeOffset = "4${my2ndSecond}" as int
		log.debug("line 416 ${new Date()} state.delayPollData '${state.delayPollData}'")
        def i = rnd.next(1)
		int delaySeconds = i % 2
        runIn((delaySeconds==0) ? firstTimeOffset : secondTimeOffset , delayPoll)
 		}
  */
 
}//end of PollSAlert


def existingFlumeAlertSync(deviceid){
	logger("line 571 ${new Date()} existingFlumeAlertSync() called","info")
	log.debug("line 572 state.myLocations ${state.myLocations}")
    
    
    //def myAlerts = pollSLAlert()
    //   state.myAlertData.add("myDeviceID":it.myDeviceID, "myLocationName":it.myLocationName,
    							//"retrieveAlertsDataResults":false,"pollAlertDelayResults":false,"inAlertMode":false)
    
    
    def deviceCounter = state.myAlertData.size()
               def nameIndex = 0
                log.debug("line 582 state.myLocations size '${deviceCounter}'   state.myAlertData '${state.myAlertData}'")
               for (def i = 0; i <deviceCounter; i++) {
               
                 if(state.myAlertData.get(i).myDeviceID==deviceid)
                {
                
                return ["myLocationName":state.myAlertData.get(i).myLocationName, "thisDeviceID":deviceid, 
                "retrieveAlertsDataResults":state.myAlertData.get(i).retrieveAlertsDataResults, "inAlertMode":state.myAlertData.get(i).inAlertMode]
                
         
                //	state.myLocations.get(i).myBridgeConnectStatus// = Flume_locations0.connected
               //    state.myLocations.get(i).myLocationName// 		= Flume_locations0.location.name
                 //  				= SL_loc.id
                //   state.myLocations.get(i).myDeviceBatteryLevel//	= Flume_locations0.battery_level 
                 //  state.myLocations.get(i).myDeviceTimeZone// 		= Flume_locations0.location.tz 
                }//end of id matches deviceid
               
               }//end of loop device data
}//end of existingFlumeAlertSync






//callback in order to initialize device
def initDevice() {
	//log.debug("line 327 '${deviceid}'")
    logger("Flume SM initDevice() called","trace")
    
    
    
    def Flume_Devices = getChildDevices()
     log.debug("line 651 Flume_Devices '${Flume_Devices}'")
    Flume_Devices?.each {
    	//if (it.deviceNetworkId == deviceid) {
    
	//Feb 5 determineFlows(it.deviceNetworkId)
    determineDeviceFlow(it.deviceNetworkId)
    /* determinehomeAway() */
    //def idToString = (state.Flume_location?.id).toString()
  def existingDevice = getChildDevice(it.deviceNetworkId)    //state.flumeDeviceId)//idToString)//idToString)
 existingDevice?.refresh()

 }
 
}


/*
def initWaterFlowData(myLocations){

state.myLocations?.each{
	
	if(it.myDeviceID==deviceid){
    	log.debug("**determineFlows:locations state.myLocations.myDeviceTimeZone** sameTZ= '${it.myDeviceTimeZone}'")
	tempDeviceTZ = it.myDeviceTimeZone
		}
	
    
    	//state.myLocations?.each{
        	state.myQueryData.add("myDeviceID":state.myLocations.myDeviceID,"today":0,"thisMonth":0,"thisYear":0,"lastHour":0,"last24Hours":0,"currentMin":0,"retrieveWaterFlowDataResults":state.lastFlowDataSuccess)
        //}
	
    
    
    }
}
*/


/*def now = new Date()
def date = new Date()
sdf = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss")
println sdf.format(date)//log.debug(now.format("yyyyMMdd-HH:mm:ss.SSS", TimeZone.getTimeZone('UTC')))
*/
//"2016-04-04 01:00:00",


def determineCloudFlow(){

def tempDeviceID = null //state.myLocations.get(i).myDeviceID

def delayWaterFlowID = null //state.myQueryData.get(state.currentDeviceIndex).myDeviceID
 def deviceCounter = state.myLocations.size()
    def nameIndex = 0
     log.debug("line 585 state.myLocations size '${deviceCounter}'    '${state.myLocations}'")
     for (def i = 0; i <deviceCounter; i++) {
    
     tempDeviceID=state.myLocations.get(i).myDeviceID	
     data.add("deviceID":tempDeviceID)
    }//
     
	//data = data as Set     
    //def data =[ device:device name, attribute: desired state (attribute)]
	//Then…
	//runIn(minute*60, checkMotion, [data: data])
 /*   
    def someEventHandler(evt) {
    runIn(60, handler, [data: [flag: true]])
}

def handler(data) {
    if (data.flag) {
        theswitch.off()
    }
}
   */ 
     
     Random rnd = new Random()
     	int i = rnd.nextInt(1)
   		int my1stSecond = rnd.nextInt(4)+1
    	int my2ndSecond = rnd.nextInt(7)+1
    	int firstTimeOffset = "3${my1stSecond}" as int
    	int secondTimeOffset = "3${my2ndSecond}" as int
		//log.debug("line 721 state.myQueryData.get(state.currentDeviceIndex).myDeviceID '${state.myQueryData.get(state.currentDeviceIndex).myDeviceID}'")
		int delaySeconds = i % 2
        log.debug("line 635 data  ${data}")
        runIn((delaySeconds==0) ? firstTimeOffset : secondTimeOffset , determineAllFlows, [data: data])
     log.debug("line 637 data  ${data}")
     //}
}

def determineAllFlows(){
 log.debug("line 706 data  ${data}")

def tempID = null
 def deviceCounter = state.myLocations.size()
    def nameIndex = 0
     log.debug("line 657 state.myLocations size '${deviceCounter}'    '${state.myLocations}'")
     for (def k = 0; k <deviceCounter; k++) { 


          ///for(def k=0; k<data.size(); k++){
tempID=state.myLocations.get(k).myDeviceID	
//data = ["thisDeviceID":tempID]
 Random rnd = new Random()
   		int my1stSecond = rnd.nextInt(4)+1
    	int my2ndSecond = rnd.nextInt(8)+1
    	int firstTimeOffset = "3${my1stSecond}000" as int
    	int secondTimeOffset = "3${my2ndSecond}000" as int
		log.debug("line 659 tempID '${tempID}'")
		int delaySeconds = k % 2
        //runIn((delaySeconds==0) ? firstTimeOffset * (k+1) : secondTimeOffset * (k+1), determineDeviceFlow, [data: ["deviceID": tempID]])
		schedule((delaySeconds==0) ? now()+(firstTimeOffset * (k+1)) : now()+(secondTimeOffset * (k+1)), determineDeviceFlow, [data: ["deviceID": tempID,overwrite: false]])
        }
     log.debug("line 665 data  ${data}")   
}





def determineDeviceFlow(data){
log.debug ("line 683 ${new Date()} string? ${(data instanceof String)} deviceid ${data} Map? ${(data instanceof Map)}")
def deviceid = (data instanceof String) ? data : data.deviceID

log.debug ("line 686 deviceid ${deviceid}")
state.messageRead = false

log.debug("line 689 determineFlows'${deviceid}'")
log.debug("line 690 state.myLocations	${state.myLocations}")
def lastMinute = null
def today = null
def lastHour = null
def last24Hours = null 
def adjustedDate = null
def adjustedTime = null
def tz = null
//  log.debug("line 373 myTimeZone ${getmyTimeZone()}    state.timeZone.get(0) '${state.myTZ.get(0)}'")   //state.timeZone.get(0)
def tempDeviceTZ = null

log.debug("line 701 state.myLocations '${state.myLocations}'")
state.myLocations?.each{
	
	if(it.myDeviceID==deviceid){
    	log.debug("**determineFlows:locations state.myLocations.myDeviceTimeZone** sameTZ= '${it.myDeviceTimeZone}'")
	tempDeviceTZ = it.myDeviceTimeZone
		}
	
    
   
    	//state.myLocations?.each{
     //   	state.myQueryData.add("myDeviceID":state.myLocations.myDeviceID,"today":0,"thisMonth":0,"thisYear":0,"lastHour":0,"last24Hours":0,"currentMin":0,"retrieveWaterFlowDataResults":state.lastFlowDataSuccess)
        //}
	
    
    
    }
 log.debug("line 718 state.myQueryData '${state.myQueryData}'  size= ${state.myQueryData.size()}")
//tempDeviceTZ = state.myTZ.get(0)

log.debug("tempDeviceTZ '${tempDeviceTZ}'")

use (groovy.time.TimeCategory) {


	//go here to find your timeZone http://www.java2s.com/Code/Java/Development-Class/DisplayAvailableTimeZones.htm and enter it in line 341
//def myTimeZone = state.timeZone.get(0)//state.timeZone.get(0)
tz = TimeZone.getTimeZone(tempDeviceTZ)     //"America/New_York") //(TimeZone.getDefault())
log.debug("line 729 tz '${tz}'")
def date = new Date()
def now = date.getTime()
def myTimeFormat = "yyyy-MM-dd HH:mm:ss"
//def sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

/*
import java.util.TimeZone
 
tz = TimeZone.getTimeZone("Asia/Jerusalem")
 
def ts = new Date()
println(ts)                                 // 12:43:14 UTC 2018
println(ts.format("HH:mm"))                 // 12:43
println(ts.format("HH:mm", timezone=tz))    // 15:43
*/


//adjustedDate = date-5.hours
//adjustedDate = sdf.setTimeZone(TimeZone.getDefault())
//adjustedDate = sdf.setTimeZone(TimeZone.getDefault()) //date-5.hours
//adjustedTime = now-5.hours

lastMinute=(date-1.minutes).format(myTimeFormat, tz)
today = date.format(myTimeFormat, tz)
lastHour = (date-60.minutes).format(myTimeFormat, tz)
last24Hours = (date-24.hours).format(myTimeFormat, tz)
/*
today = sdf.format(date)
lastHour = sdf.format(date-120.minutes)
last24Hours = sdf.format(date-24.hours)
*/
}//end of determineFlow

log.debug("myDefault Timezone '${tz}'")
log.debug("lastMinute '${lastMinute}'")
log.debug("todayTime '${today}'")
log.debug("lastHourTime '${lastHour}'")
log.debug("last24HoursTime '${last24Hours}'")

/*
def myBook = new MyBook(isbn: '0321774094',
  title: 'Scala for the Impatient',
  author: 'Cay S. Horstmann',
  publisher: 'Addison-Wesley Professional')
  */
  /*
  def todayUsage = new TodayUsage(
  	request_id: 'today',
  	bucket: "DAY",
    since_datetime: today
  )
jsonBuilder(queries: todayUsage)   //books: myBookList)
log.debug("jsonBuilder.toPrettyString() '${jsonBuilder.toPrettyString()}'")
*/

	 def body = new groovy.json.JsonOutput().toJson([
           
           queries:[ 
           
           [
            request_id: "today",
            bucket: "DAY",
            since_datetime: today,
            ],
            
            [
            request_id: "thisMonth",
            bucket: "MON",
            since_datetime: today,
            ],
            
            [
            request_id: "thisYear",
            bucket: "YR",
            since_datetime: today,
            ],
            
            
            [
             request_id: "lastHour",
             operation: "SUM",
            bucket: "MIN",
            since_datetime: lastHour,
            ],
            
            [
             request_id: "last24Hours",
             operation: "SUM",
            bucket: "HR",
            since_datetime: last24Hours,
            ],
            
            [
             request_id: "currentMin",
            bucket: "MIN",
            since_datetime: lastMinute,
            ],
            
   		 ],
    ])
    //good debug stuff json
   // def bodyString = new groovy.json.JsonOutput().prettyPrint(body)
	  //log.debug("body output'${body}'")
      //log.debug("bodyString output '${bodyString}'")
      
    logger("line 835 determineDeviceFlow() called","info")
    //def idToString = (state.Flume_location?.id).toString()
    def deviceCounter = state.myLocations.size()
    def nameIndex = 0
     log.debug("line 839 state.myLocations size '${deviceCounter}'    '${state.myLocations}'")
  //   for (def i = 0; i <deviceCounter; i++) {
    def tempDeviceID= deviceid//state.myLocations.get(i).myDeviceID
    def existingDevice = getChildDevice(tempDeviceID) //state.flumeDeviceId) //idToString) //need to do the ? to see what it does
  	log.debug("line 843 tempDeviceID '${tempDeviceID}' determineFlows(): state.flumeDeviceId '${deviceid}'") //state.flumeDeviceId}'")
    
	if (existingDevice){
       def params = [
            uri:  getApiBase(),
			path: "users/${flumeUserId}/devices/${tempDeviceID}/query",      //state.flumeDeviceId}/query",         
			headers: getDefaultAuthHeaders(),
            body: body   //bodyString
    ]
    log.debug("try params output'${params}'")
    
    def tempThisCurrentMinFlow = null
    def tempToday = null
    def tempThisMonthFlow = null
    def tempThisYearFlow = null
    def tempLast24Hour = null
    def tempLastHour = null
    
 //   if(state.myQueryData.get(i).waterFlowDelayResults==false){
        try {
        		                
                httpPostJson(params) {resp ->
                    def resp_data = resp.data ///def bodyString = new groovy.json.JsonOutput().prettyPrint(body)
                     //def responseBodyString = new groovy.json.JsonOutput().prettyPrint(resp_data)
                     //log.debug("responseBodyString output '${responseBodyString}'")
                    //def slurper = new JsonSlurper().parseText(resp_data)
                    /*
                    def state.todayFlow = null
                	def state.thisMonthFlow = null
                	def state.thisYear = null
					*/
    
                    if (resp.status == 200){//successful retrieve
                             
                   logger("line 961 ${new Date()} determineDeviceFlow successfully called ${deviceid}","info")
                 //   if(state.myQueryData.get(i).myDeviceID==tempDeviceID){
                                  
                   // state.lastFlowDataSuccess=true
              //     state.myQueryData.get(i).waterFlowDelayResults=false
                    log.debug("line 966 **determineDeviceFlow()**  ${deviceid} resp_data success 200 '${resp_data}'")
                    //log.debug("resp_data.data'${resp_data.data}'")
                   // log.debug("resp_data.data.today.value '${resp_data.data.today.value}'")        //.value returns [[0]] .value[0] returns [0]
                      
                //this should have worked? log.debug("resp.status '${resp.status}'")
    			//logger("Flume SM determineFlows resp.data: ${resp.data}","debug")
                
                //log.debug("outside of if success condition resp.data '${resp.data}'")
                //log.debug("device queris result '${resp_data}'")
           
            	//log.debug("resp_data?.data.today.value '${resp_data?.data.today.value}'")
                 //log.debug("resp_data.data.today.value '${resp_data.data.today.value}'")
              // def myWaterValues = resp_data.data.getAt('thisMonth')//.size()
             //  log.debug(" getAt('thisMonth') myWaterValues '${myWaterValues}'") 
                    
                   tempThisCurrentMinFlow = (resp_data.data.currentMin?.value[0][0]).toDouble()   //.toInteger() //!= null ?  (resp_data.data.currentMin.value[0][0]).toInteger() : 0  //(resp_data.data.getAt('currentMin').getAt('value')[0][0]).toInteger()  //resp_data.data.thisMonth.value
                   log.debug("line 771 tempthisCurrentMinFlow  '${tempThisCurrentMinFlow}'")
                 
               tempToday = (resp_data.data.today.value[0][0]).toDouble()            //.toInteger()
               log.debug("line 774 tempToday '${tempToday}'")
               
               tempThisMonthFlow = (resp_data.data.getAt('thisMonth').getAt('value')[0][0]).toDouble()   //.toInteger()  //resp_data.data.thisMonth.value
                 log.debug("line 777 tempThisMonthFlow '${tempThisMonthFlow}'")
               
               tempThisYearFlow = (resp_data.data.thisYear.value[0][0]).toDouble()
                 log.debug("line 780 tempThisYearFlow '${tempThisYearFlow}'")
                 
                 tempLast24Hour = (resp_data.data.last24Hours.value[0][0]).toDouble()
    			log.debug("line 998 tempThisYearFlow '${tempLast24Hour}'")
                
                tempLastHour = (resp_data.data.lastHour.value[0][0]).toDouble()
               log.debug("line 1001 tempThisYearFlow '${tempLastHour}'")
                 
            //     }//itemid condition
                 
                 
                 
	for(def j=0; j<state.myQueryData.size(); j++){

				if(state.myQueryData.get(j).myDeviceID==deviceid){
         
           		state.myQueryData.get(j).currentMin = tempThisCurrentMinFlow
                 state.myQueryData.get(j).today = tempToday
                 state.myQueryData.get(j).thisMonth = tempThisMonthFlow
                 state.myQueryData.get(j).thisYear = tempThisYearFlow
                 state.myQueryData.get(j).retrieveWaterFlowDataResults = true //state.lastFlowDataSuccess
                 
                   state.myQueryData.get(j).last24Hours = tempLast24Hour
                  state.myQueryData.get(j).lastHour = tempLastHour
         }
         }
                 
                 
                 
                 //Nov 24 Begin
                 /*
                  def myTempCurrentMin =  state.thisCurrentMinFlow //at this point null? Math.round(cloudData.thisCurrentMinFlow)  try this too: state.thisCurrentMinFlow.value
    def myTempTodayFlow = state.todayFlow
    def myTempMonthFlow = state.thisMonthFlow
    def myTempYearFlow =     String.format("%,.2f", (state.thisYearFlow).toDouble() )  
                 
                   sendEvent(name: "Current Minute", value: "Currently: '${myTempCurrentMin}' GPM")
                   sendEvent(name: "Today", value: "Currently: '${myTempTodayFlow}' Gallon(s)")
                   sendEvent(name: "Monthly", value: "Currently: '${myTempMonthFlow}' Gallon(s)")
                   sendEvent(name: "Yearly", value: "Currently: '${myTempYearFlow}' Gallon(s)")

		
        */
        //Nov 24 End
                 
                 //state.unitsFlow = resp_data?.units
                
            		}//end of status 200 ok
                    else{
                     log.debug("fail httpPostJson(params)")
                    }
         }//end of resp
         
         log.debug("line 951 state.myQueryData ${state.myQueryData}")
         
         
         
        } catch (e) {
    		logger("line 956 device ${deviceid} determineDeviceFlow() error retrieving summary data e= ${e}","error")
		/*
        	state.currentDeviceIndex = i 
            state.lastFlowDataSuccess=false
            state.myQueryData.get(i).retrieveWaterFlowDataResults=false
            state.myQueryData.get(i).waterFlowDelayResults=true
          */
          //state.delayPollData = true
		//	state.delayFlowData = true
			//state.delaySyncData = false
            
            /* disabled Nov 162020: debug PollAlert needed
            state.thisCurrentMinFlow = 0
            state.todayFlow = 0
            state.thisMonthFlow = 0
            state.thisYearFlow = 0
            */
            //state.unitsFlow = "gallons"
            
        }//end catch
        
  //  }//end of (state.myQueryData.get(i).waterFlowDelayResults==false)
 		/*
        if(state.myQueryData.get(i).waterFlowDelayResults==true){
        Random rnd = new Random()
   		int my1stSecond = rnd.nextInt(4)+1
    	int my2ndSecond = rnd.nextInt(7)+1
    	int firstTimeOffset = "3${my1stSecond}" as int
    	int secondTimeOffset = "3${my2ndSecond}" as int
		log.debug("line 721 state.myQueryData.get(state.currentDeviceIndex).myDeviceID '${state.myQueryData.get(state.currentDeviceIndex).myDeviceID}'")
		int delaySeconds = i % 2
        runIn((delaySeconds==0) ? firstTimeOffset : secondTimeOffset , delayFlowData)
 		}
        
        */
 }//end of existing
 
 //def myTempDeviceID = state.myQueryData.get(i).myDeviceID  //${state.myQueryData.get(i).myDeviceID}
 //log.debug("line 729 tempDeviceID  '${tempDeviceID}'    myTempDeviceID   '${myTempDeviceID}")
// printTest()
 
// }//end for each location loop
   // runOnce(new Date(),printTest)
    log.debug("line 874 deviceid ${deviceid} state.myQueryData '${state.myQueryData}'    size ${state.myQueryData.size()}")
  //  runOnce(new Date(),printTest(tempDeviceID))
//	//state.myQueryData.get(j) = [currentMin:0.0, last24Hours:0, lastHour:0, myDeviceID:6715067545444607421,
    //retrieveWaterFlowDataResults:true, thisMonth:3284.8087698, thisYear:3284.8087698, today:110.81761146, waterFlowDelayResults:false]

//return state.myQueryData
/*
	for(def j=0; j<state.myQueryData.size(); j++){

				if(state.myQueryData.get(j).myDeviceID==deviceid){ //&&  state.myQueryData.get(j).retrieveWaterFlowDataResults==true){
                log.debug("line 745 state.myQueryData.get(j) = ${state.myQueryData.get(j)}")
				return 	["currentMin":state.myQueryData.get(j).currentMin, "last24Hours":state.myQueryData.get(j).last24Hours, "lastHour":state.myQueryData.get(j).lastHour,
                		"myDeviceID":state.myQueryData.get(j).myDeviceID,"retrieveWaterFlowDataResults":state.myQueryData.get(j).retrieveWaterFlowDataResults,
                        "thisMonth":state.myQueryData.get(j).thisMonth, "thisYear":state.myQueryData.get(j).thisYear, "today":state.myQueryData.get(j).today,
                        "waterFlowDelayResults":state.myQueryData.get(j).waterFlowDelayResults]
																													//state.myQueryData.get(j)
				}//end of if
             }//end of loop
*/

}//end of determineDeviceflows





//determine flow totals from cloud
def determineFlows(deviceid){
state.messageRead = false

log.debug("line 356 determineFlows'${deviceid}'")
log.debug("line 410 state.myLocations	${state.myLocations}")
def lastMinute = null
def today = null
def lastHour = null
def last24Hours = null 
def adjustedDate = null
def adjustedTime = null
def tz = null
//  log.debug("line 373 myTimeZone ${getmyTimeZone()}    state.timeZone.get(0) '${state.myTZ.get(0)}'")   //state.timeZone.get(0)
def tempDeviceTZ = null

log.debug("line 420 state.myLocations '${state.myLocations}'")
state.myLocations?.each{
	
	if(it.myDeviceID==deviceid){
    	log.debug("**determineFlows:locations state.myLocations.myDeviceTimeZone** sameTZ= '${it.myDeviceTimeZone}'")
	tempDeviceTZ = it.myDeviceTimeZone
		}
	
    
   
    	//state.myLocations?.each{
     //   	state.myQueryData.add("myDeviceID":state.myLocations.myDeviceID,"today":0,"thisMonth":0,"thisYear":0,"lastHour":0,"last24Hours":0,"currentMin":0,"retrieveWaterFlowDataResults":state.lastFlowDataSuccess)
        //}
	
    
    
    }
 log.debug("line 464 state.myQueryData '${state.myQueryData}'  size= ${state.myQueryData.size()}")
//tempDeviceTZ = state.myTZ.get(0)

log.debug("tempDeviceTZ '${tempDeviceTZ}'")

use (groovy.time.TimeCategory) {


	//go here to find your timeZone http://www.java2s.com/Code/Java/Development-Class/DisplayAvailableTimeZones.htm and enter it in line 341
//def myTimeZone = state.timeZone.get(0)//state.timeZone.get(0)
tz = TimeZone.getTimeZone(tempDeviceTZ)     //"America/New_York") //(TimeZone.getDefault())
log.debug("line 475 tz '${tz}'")
def date = new Date()
def now = date.getTime()
def myTimeFormat = "yyyy-MM-dd HH:mm:ss"
//def sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

/*
import java.util.TimeZone
 
tz = TimeZone.getTimeZone("Asia/Jerusalem")
 
def ts = new Date()
println(ts)                                 // 12:43:14 UTC 2018
println(ts.format("HH:mm"))                 // 12:43
println(ts.format("HH:mm", timezone=tz))    // 15:43
*/


//adjustedDate = date-5.hours
//adjustedDate = sdf.setTimeZone(TimeZone.getDefault())
//adjustedDate = sdf.setTimeZone(TimeZone.getDefault()) //date-5.hours
//adjustedTime = now-5.hours

lastMinute=(date-1.minutes).format(myTimeFormat, tz)
today = date.format(myTimeFormat, tz)
lastHour = (date-60.minutes).format(myTimeFormat, tz)
last24Hours = (date-24.hours).format(myTimeFormat, tz)
/*
today = sdf.format(date)
lastHour = sdf.format(date-120.minutes)
last24Hours = sdf.format(date-24.hours)
*/
}//end of determineFlow

log.debug("myDefault Timezone '${tz}'")
log.debug("lastMinute '${lastMinute}'")
log.debug("todayTime '${today}'")
log.debug("lastHourTime '${lastHour}'")
log.debug("last24HoursTime '${last24Hours}'")

/*
def myBook = new MyBook(isbn: '0321774094',
  title: 'Scala for the Impatient',
  author: 'Cay S. Horstmann',
  publisher: 'Addison-Wesley Professional')
  */
  /*
  def todayUsage = new TodayUsage(
  	request_id: 'today',
  	bucket: "DAY",
    since_datetime: today
  )
jsonBuilder(queries: todayUsage)   //books: myBookList)
log.debug("jsonBuilder.toPrettyString() '${jsonBuilder.toPrettyString()}'")
*/

	 def body = new groovy.json.JsonOutput().toJson([
           
           queries:[ 
           
           [
            request_id: "today",
            bucket: "DAY",
            since_datetime: today,
            ],
            
            [
            request_id: "thisMonth",
            bucket: "MON",
            since_datetime: today,
            ],
            
            [
            request_id: "thisYear",
            bucket: "YR",
            since_datetime: today,
            ],
            
            
            [
             request_id: "lastHour",
             operation: "SUM",
            bucket: "MIN",
            since_datetime: lastHour,
            ],
            
            [
             request_id: "last24Hours",
             operation: "SUM",
            bucket: "HR",
            since_datetime: last24Hours,
            ],
            
            [
             request_id: "currentMin",
            bucket: "MIN",
            since_datetime: lastMinute,
            ],
            
   		 ],
    ])
    //good debug stuff json
   // def bodyString = new groovy.json.JsonOutput().prettyPrint(body)
	  //log.debug("body output'${body}'")
      //log.debug("bodyString output '${bodyString}'")
      
    logger("Flume SM determineFlows() called","trace")
    //def idToString = (state.Flume_location?.id).toString()
    def deviceCounter = state.myLocations.size()
    def nameIndex = 0
     log.debug("line 585 state.myLocations size '${deviceCounter}'    '${state.myLocations}'")
     for (def i = 0; i <deviceCounter; i++) {
    def tempDeviceID= state.myLocations.get(i).myDeviceID
    def existingDevice = getChildDevice(tempDeviceID) //state.flumeDeviceId) //idToString) //need to do the ? to see what it does
  	log.debug("line 589 tempDeviceID '${tempDeviceID}' determineFlows(): state.flumeDeviceId '${deviceid}'") //state.flumeDeviceId}'")
    
	if (existingDevice){
       def params = [
            uri:  getApiBase(),
			path: "users/${flumeUserId}/devices/${tempDeviceID}/query",      //state.flumeDeviceId}/query",         
			headers: getDefaultAuthHeaders(),
            body: body   //bodyString
    ]
    log.debug("try params output'${params}'")
    
    
    if(state.myQueryData.get(i).waterFlowDelayResults==false){
        try {
        		                
                httpPostJson(params) {resp ->
                    def resp_data = resp.data ///def bodyString = new groovy.json.JsonOutput().prettyPrint(body)
                     //def responseBodyString = new groovy.json.JsonOutput().prettyPrint(resp_data)
                     //log.debug("responseBodyString output '${responseBodyString}'")
                    //def slurper = new JsonSlurper().parseText(resp_data)
                    /*
                    def state.todayFlow = null
                	def state.thisMonthFlow = null
                	def state.thisYear = null
					*/
    
                    if (resp.status == 200){//successful retrieve
                    
                    if(state.myQueryData.get(i).myDeviceID==tempDeviceID){
                                  
                   // state.lastFlowDataSuccess=true
                   state.myQueryData.get(i).waterFlowDelayResults=false
                    log.debug("**determineFlows** resp_data success 200 '${resp_data}'")
                    //log.debug("resp_data.data'${resp_data.data}'")
                   // log.debug("resp_data.data.today.value '${resp_data.data.today.value}'")        //.value returns [[0]] .value[0] returns [0]
                      
                //this should have worked? log.debug("resp.status '${resp.status}'")
    			//logger("Flume SM determineFlows resp.data: ${resp.data}","debug")
                
                //log.debug("outside of if success condition resp.data '${resp.data}'")
                //log.debug("device queris result '${resp_data}'")
           
            	//log.debug("resp_data?.data.today.value '${resp_data?.data.today.value}'")
                 //log.debug("resp_data.data.today.value '${resp_data.data.today.value}'")
              // def myWaterValues = resp_data.data.getAt('thisMonth')//.size()
             //  log.debug(" getAt('thisMonth') myWaterValues '${myWaterValues}'") 
                    
                   state.thisCurrentMinFlow = (resp_data.data.currentMin?.value[0][0]).toDouble()   //.toInteger() //!= null ?  (resp_data.data.currentMin.value[0][0]).toInteger() : 0  //(resp_data.data.getAt('currentMin').getAt('value')[0][0]).toInteger()  //resp_data.data.thisMonth.value
                   log.debug("state.thisCurrentMinFlow  '${state.thisCurrentMinFlow}'")
                 
               state.todayFlow = (resp_data.data.today.value[0][0]).toDouble()            //.toInteger()
               log.debug("state.todayFlow '${ state.todayFlow}'")
               
               state.thisMonthFlow = (resp_data.data.getAt('thisMonth').getAt('value')[0][0]).toDouble()   //.toInteger()  //resp_data.data.thisMonth.value
                 log.debug("state.thisMonthFlow  '${state.thisMonthFlow }'")
               
               state.thisYearFlow = (resp_data.data.thisYear.value[0][0]).toDouble()
                 log.debug("state.thisYearFlow '${state.thisYearFlow}'")
                 
                 state.myQueryData.get(i).currentMin = state.thisCurrentMinFlow
                 state.myQueryData.get(i).today = state.todayFlow
                 state.myQueryData.get(i).thisMonth = state.thisMonthFlow
                 state.myQueryData.get(i).thisYear = state.thisYearFlow
                 state.myQueryData.get(i).retrieveWaterFlowDataResults = true //state.lastFlowDataSuccess
                 
                 }//itemid condition
                 
                 
                 //Nov 24 Begin
                 /*
                  def myTempCurrentMin =  state.thisCurrentMinFlow //at this point null? Math.round(cloudData.thisCurrentMinFlow)  try this too: state.thisCurrentMinFlow.value
    def myTempTodayFlow = state.todayFlow
    def myTempMonthFlow = state.thisMonthFlow
    def myTempYearFlow =     String.format("%,.2f", (state.thisYearFlow).toDouble() )  
                 
                   sendEvent(name: "Current Minute", value: "Currently: '${myTempCurrentMin}' GPM")
                   sendEvent(name: "Today", value: "Currently: '${myTempTodayFlow}' Gallon(s)")
                   sendEvent(name: "Monthly", value: "Currently: '${myTempMonthFlow}' Gallon(s)")
                   sendEvent(name: "Yearly", value: "Currently: '${myTempYearFlow}' Gallon(s)")

		
        */
        //Nov 24 End
                 
                 //state.unitsFlow = resp_data?.units
                
            		}//end of status 200 ok
                    else{
                     log.debug("fail httpPostJson(params)")
                    }
         }//end of resp
        } catch (e) {
    		logger("line 645 Flume SM determineFlows error retrieving summary data e= ${e}","error")
			state.currentDeviceIndex = i 
            state.lastFlowDataSuccess=false
            state.myQueryData.get(i).retrieveWaterFlowDataResults=false
            state.myQueryData.get(i).waterFlowDelayResults=true
            //state.delayPollData = true
			state.delayFlowData = true
			//state.delaySyncData = false
            
            /* disabled Nov 162020: debug PollAlert needed
            state.thisCurrentMinFlow = 0
            state.todayFlow = 0
            state.thisMonthFlow = 0
            state.thisYearFlow = 0
            */
            //state.unitsFlow = "gallons"
            
        }//end catch
        
    }//end of (state.myQueryData.get(i).waterFlowDelayResults==false)
 		
        if(state.myQueryData.get(i).waterFlowDelayResults==true){
        Random rnd = new Random()
   		int my1stSecond = rnd.nextInt(4)+1
    	int my2ndSecond = rnd.nextInt(7)+1
    	int firstTimeOffset = "3${my1stSecond}" as int
    	int secondTimeOffset = "3${my2ndSecond}" as int
		log.debug("line 721 state.myQueryData.get(state.currentDeviceIndex).myDeviceID '${state.myQueryData.get(state.currentDeviceIndex).myDeviceID}'")
		int delaySeconds = i % 2
        runIn((delaySeconds==0) ? firstTimeOffset : secondTimeOffset , delayFlowData)
 		}
 }//end of existing
 
 def myTempDeviceID = state.myQueryData.get(i).myDeviceID  //${state.myQueryData.get(i).myDeviceID}
 log.debug("line 729 tempDeviceID  '${tempDeviceID}'    myTempDeviceID   '${myTempDeviceID}")
// printTest()
 
 }//end for each location loop
   // runOnce(new Date(),printTest)
    
     
    log.debug("line 736 state.myQueryData '${state.myQueryData}'    size ${state.myQueryData.size()}")
  //  runOnce(new Date(),printTest(tempDeviceID))
//	//state.myQueryData.get(j) = [currentMin:0.0, last24Hours:0, lastHour:0, myDeviceID:6715067545444607421,
    //retrieveWaterFlowDataResults:true, thisMonth:3284.8087698, thisYear:3284.8087698, today:110.81761146, waterFlowDelayResults:false]

//return state.myQueryData
/*
	for(def j=0; j<state.myQueryData.size(); j++){

				if(state.myQueryData.get(j).myDeviceID==deviceid){ //&&  state.myQueryData.get(j).retrieveWaterFlowDataResults==true){
                log.debug("line 745 state.myQueryData.get(j) = ${state.myQueryData.get(j)}")
				return 	["currentMin":state.myQueryData.get(j).currentMin, "last24Hours":state.myQueryData.get(j).last24Hours, "lastHour":state.myQueryData.get(j).lastHour,
                		"myDeviceID":state.myQueryData.get(j).myDeviceID,"retrieveWaterFlowDataResults":state.myQueryData.get(j).retrieveWaterFlowDataResults,
                        "thisMonth":state.myQueryData.get(j).thisMonth, "thisYear":state.myQueryData.get(j).thisYear, "today":state.myQueryData.get(j).today,
                        "waterFlowDelayResults":state.myQueryData.get(j).waterFlowDelayResults]
																													//state.myQueryData.get(j)
				}//end of if
             }//end of loop
*/

}//end of determine flows
 
 


def delayPoll(){
logger("delayPoll() 30-40ish random seconds wait is over, rate limit window available","info")
state.delayPollData = false
//pollSLAlert()
}

def delayFlowData(){
def delayWaterFlowID = state.myQueryData.get(state.currentDeviceIndex).myDeviceID
logger("line 752 delayFlowData() deviceID ${delayWaterFlowID} : 30ish seconds wait is over, rate limit window available","info")
//state.myQueryData.get(deviceIndex).retrieveWaterFlowDataResults 
state.myQueryData.get(state.currentDeviceIndex).waterFlowDelayResults=false
determineFlows(delayWaterFlowID)
//state.delayFlowData = false
}

def delaySync(){
logger("delaySync() 30ish seconds wait is over, rate limit window available","info")
state.delaySyncData = false
//syncLocationData()
}


def delayIninitialize(){
log.debug("delayIninitialize() 30ish seconds wait is over, rate limit window available")
state.initializeDelay = false
//initFlume_locations(flumeUserId)
}

 
def printTest(){
def myLocationsSize = state.myLocations.size()
if(myLocationsSize==4){
//state.myLocations.clear()
log.debug("line 671 state.myLocations.clear() printTest clearing state.myLocations.size() ${myLocationsSize}")
}

def timeCounter = 30

for (def i = 0; i <timeCounter; i++) {

log.debug("deviceID ${tempDeviceID} printTest ${timeCounter}: ${i}")

}
log.debug("line 722 state.myQueryData '${state.myQueryData}' \n printTest clearing state.myLocations.size() ${myLocationsSize}")
}



//determine Flume home/away from Flume cloud

/* def determinehomeAway() {
    logger("Flume SM determinehomeAway() called","trace")
    def existingDevice = getChildDevice(state.Flume_location?.locationId)
	if (existingDevice){
        def params = [
                uri:  'https://api.Flumewater.com/v1/locations/' + state.Flume_location.locationId,
                headers: ['Authorization': 'Bearer ' + appSettings.api_key],
                contentType: 'application/json',
                ]
		try {
            httpGet(params) {resp ->
    			logger("Flume SM determinehomeAway resp.data: ${resp.data}; resp.status: ${resp.status}","debug")
                if (resp.status == 200){//successful retrieve
                    def resp_data = resp.data
                    state.homeAway = resp_data?.homeAway
                }
            }
        } catch (e) {
    		logger("Flume SM determinehomeAway error: $e","error")
        }
    }
} */
/*
    def body = new groovy.json.JsonOutput().toJson([
            "email"   : settings.email,
            "password": settings.password
    ])
    def params = [
        uri: getApiBase(),
        path: "auth",
        headers: getDefaultHeaders(),
        body: body
    ]
*/




//Get desired location from Flume cloud based on user's entered location's name
def initFlume_locations(flumeUserId) {
	log.debug("line 1528 initFlume_locations(flumeUserId) = ${flumeUserId}")
    logger("Flume SM initFlume_locations() called","trace")
    /*
        def qs = new groovy.json.JsonOutput().toJson(
        	[
                "user": false,
                "location": true
             ])
    log.debug("qs ${qs}")*/
    def params = [
            uri:  getApiBase(),
			path: "users/${flumeUserId}/devices",  
            query: [
           		 "user": false,
                "location": true //false //true
            ],
            headers: getDefaultAuthHeaders()
            ]
    state.Flume_location = null
	state.flumeDeviceId = null
    	
     def tempLocationName = null
     def tempLocationDeviceId = null
     def tempLocationBattery = null
     def tempLocationTimeZone = null
     def tempLocationConnectedStatus = null
     
       def devicesList = []
       def batteryLevelList = []
       def deviceLocationName = []
       def deviceTimeZoneList = []
       def deviceConectedStatus = []
       
      // def myLocations =[]
       
//log.debug("params ${params}")
	try {
    
    	
    if(state.initializeDelay==false){
        httpGet(params) {resp ->
            ////log.debug("resp.size() === ${resp.size()} ") 
            def resp_data = resp.data
          //  def Flume_locations1 = resp_data.data[1]
          //  log.debug("resp '${resp}'")
           // log.debug("Flume_locations1 '${Flume_locations1}'")
            log.debug("line 1575 resp_data '${resp_data}'")
            // /* might Nov 28 need to delete
            def Flume_locations0 = resp_data.data[0]
			//state.flumeDeviceId = Flume_locations0.id
            //Nov 28 need to delete Flume_locations useless
			log.debug("line 1580 Flume_locations0 '${Flume_locations0}'")
			//log.debug("line 627 flumeDeviceId '${state.flumeDeviceId}'")
           // log.debug("Flume_locations0.location '${Flume_locations0.location}'")
			//log.debug("Flume_locations0.location.name '${Flume_locations0.location.name}'")
           // log.debug("resp.data.data.location '${resp.data.data.location}'")
           //   might Nov 28 need to delete            */
            def ttl = resp_data.count
            /*
            def mymap = [name:"Gromit", id:1234]
def x = mymap.find{ it.key == "likes" }?.value
if(x)
    println "x value: ${x}"

println x.getClass().name
            */
            
            log.debug("resp_data.count '${ttl}'")
    
    		//resp.data.each{SL_loc->
            def counter = 0
          
             resp.data.data.each{SL_loc->
     			counter++
           	//	tempLocationName = SL_loc.location.name
            
            /*
            def emptyList = []
assert emptyList.size() == 0
emptyList.add(5) */

//return ["todayFlow":state.todayFlow, "thisMonthFlow":state.thisMonthFlow, 
//      "thisYearFlow":state.thisYearFlow,  /*"homeAway":state.homeAway, */ "inAlert":state.inAlert, "thisCurrentMinFlow":state.thisCurrentMinFlow]


                if(SL_loc.battery_level?.value)
                {
                	tempLocationDeviceId = SL_loc.find{it.key=='id'}?.value
                 devicesList.add(tempLocationDeviceId)
                 deviceLocationName.add(SL_loc.location.name)
                 batteryLevelList.add(SL_loc.battery_level)
                 tempLocationBattery = SL_loc.battery_level
               
                  deviceTimeZoneList.add(SL_loc.location.tz)
                  tempLocationTimeZone = SL_loc.location.tz
                  deviceConectedStatus.add(SL_loc.connected)
                  tempLocationConnectedStatus = SL_loc.connected
                   state.myLocations.add("myLocationName": SL_loc.location.name, "myDeviceID": SL_loc.id, "myDeviceBatteryLevel":SL_loc.battery_level, "myBridgeConnectStatus": SL_loc.connected, "myDeviceTimeZone":SL_loc.location.tz )
                }
                
               // tempLocationBattery += SL_loc.battery_level?.value
               // batteryLevelList.
             //   tempLocationTimeZone = SL_loc.location.tz
                
                //log.debug("counter ${counter} line 643 tempLocationName /*'${tempLocationName}' */  tempDeviceID '${tempLocationDeviceId} ")
                 log.debug("line 969  loop counter ${counter} tempDeviceID '${tempLocationDeviceId}'       deviceList '${devicesList}'        deviceLocationName '${deviceLocationName}'   deviceTimeZoneList   '${deviceTimeZoneList} ")  //, "warn")
                //state.flumeDeviceId = tempLocationDeviceId                    //Flume_locations0.id
             //   log.debug("tempDeviceID '${tempLocationDeviceId}")
                //log.debug("tempLocationBattery '${tempLocationBattery}'")
                state.myTZ = deviceTimeZoneList
                state.myBatterLevel = batteryLevelList 
				state.myConnectedSatus = deviceConectedStatus
                
               log.debug("line 1641 state.timeZone = deviceTimeZoneList '${state.myTZ}"   )
         		
              //  log.debug ("line 651 true or false ${tempLocationName.equalsIgnoreCase(userFlume_locName)}")
              /*
             //   if ( /*tempLocationName*/ // SL_loc.location.name.equalsIgnoreCase(userFlume_locName)==true) { //Let user enter without worrying about case
             //        state.Flume_location = SL_loc  //resp.data.data[myCounter]//SL_loc //all data resp
              //       state.flumeDeviceId = SL_loc.id
              //       log.debug("inside line 654 SL_loc.location.name ${SL_loc.location.name}    state.flumeDeviceId ${state.flumeDeviceId}      \n SL_loc  ${SL_loc}")


					}//end for each location
    
    			log.debug("line 1653 state.myLocations	${state.myLocations.size()}")
                
               //   */
            }//end response
            
            for (def i = 0; i <deviceTimeZoneList.size(); i++) {
             
            if(!tempLocationTimeZone.equals(state.myTZ.get(i))){
            	state.sameTZ = false
                break
                }
            }
           
            
            state.sameBatteryLevel = true
           state.sameConnectedStatus = true
      		//state.myLocations = myLocations
            log.debug("line 1670 state.timeZone.get(0) '${state.myTZ.get(0)}"   )
                //logger("outside line 657 state.Flume_location ${state.Flume_location}", "warn")
             //log.debug("final loop count value ${myCounter} ")
          
          
          //if (!state.Flume_location){         //!tempLocationName){  //!state.Flume_location) {
		 //   	logger("Flume SM in initFlume_locations- Flume location name: ${userFlume_locName} not found!","warn")
        //    }// else {
            
			/*
			 def params = [
            uri:  getApiBase(),
			path: "users/${flumeUserId}/devices",         
			headers: getDefaultAuthHeaders(),
			]
			try {
        httpGet(params) {resp ->
		def resp_dataDevices = resp.data
		
		}*/
			
			
            
            ////log.debug("else condition existing Device")
            	//save current homeAway status
                //state.homeAway = state.Flume_location.homeAway 
                
                //create device handler for this location (device)
                //def idToString = (state.Flume_location.id).toString()
                
                //state.timeZone = deviceTimeZoneList.get(0)
               def deviceCounter = 0
               def nameIndex = 0
                log.debug("line 944 state.myLocations '${state.myLocations}'")
               state.myLocations?.each{
            	
                log.debug("line 906 ${deviceCounter++} it '${it.myDeviceID}'")
              
              def existingDevice = getChildDevice(it.myDeviceID)//                idToString)   //state.Flume_location.locationId)
              //   //log.debug("line 416 existingDevice getChildDevice state.Flume_location.locationId=== ${state.Flume_location.get('id')}") //state.Flume_location.locationId}")
               if(!existingDevice) {
               
                // //log.debug("line 418 !existingDevice === ${existingDevice}")
              //  log.debug("line 681 state.flumeDeviceId ${state.flumeDeviceId}   tempLocationDeviceId ${tempLocationDeviceId}")
               def childDevice = addChildDevice("getterdone", "OneApp Flume Water Flow Meter DH",   it.myDeviceID , null, 
               [name: "${it.myLocationName}-Flume Meter", label: " ${it.myLocationName/*deviceLocationName.get(nameIndex)*/}-Flume Meter" /*"#${deviceCounter}-Flume Meter" */ , completedSetup: true])
               nameIndex= nameIndex+1
               
                state.myQueryData.add("myDeviceID":it.myDeviceID,"today":0,"thisMonth":0,"thisYear":0,"lastHour":0,"last24Hours":0,"currentMin":0,"retrieveWaterFlowDataResults":state.lastFlowDataSuccess,"waterFlowDelayResults":false,"myLocationName":it.myLocationName)
                state.myAlertData.add("myDeviceID":it.myDeviceID, "myLocationName":it.myLocationName,"retrieveAlertsDataResults":false,"pollAlertDelayResults":false,"inAlertMode":false)
               
                  // def childDevice = addChildDevice("getterdone", "Flume Water Flow DH", state.flumeDeviceId /*idToString*/, null, [name: "Flume Water Flow DH", label: "Flume Water - ${}" , completedSetup: true])
		    	//	logger("Flume SM initFlume_locations- device created with Id: ${state.Flume_location.get('id')/*locationId*/} for Flume_location: ${state.Flume_location.get('name')/*name*/}","info")
					////log.debug("Flume SM initFlume_locations- device created with Id: ${state.Flume_location.get('id')/*locationId*/} for Flume_location: ${state.Flume_location.get('name')/*name*/} ") 
             } else {
		    		log.debug("line 1725Flume SM initFlume_locations- device not created; already exists: ${existingDevice.getDeviceNetworkId()}","warn")
             
             
             } 
                
            }//end of myLocations loop
     }//end delayCondtion
    } catch (e) {
    state.initializeDelay=true
		logger("line 1734 Flume SM error in initFlume_locations retrieving locations: ${e}","error")
        
    }
		//log.debug("line 981 state.myLocations  '${state.myLocations}'")
     /*  
	if(state.initializeDelay){
     Random rnd = new Random()
   		int my1stSecond = rnd.nextInt(4)+1
    	int my2ndSecond = rnd.nextInt(8)+1
    	int firstTimeOffset = "3${my1stSecond}" as int
    	int secondTimeOffset = "3${my2ndSecond}" as int
		log.debug("line 1743 state.initializeDelay '${state.initializeDelay}'")
        def i = rnd.next(1)
		int delaySeconds = i % 2
        runIn((delaySeconds==0) ? firstTimeOffset : secondTimeOffset , delayIninitialize)
    	   }
		*/           
           
    log.debug("line 983 state.myQueryData  '${state.myQueryData}'    state.myAlertData   '${state.myAlertData}")
}

def myRandomSyncLocationData(){

 Random rnd = new Random()
   		int my1stSecond = rnd.nextInt(4)+1
    	int my2ndSecond = rnd.nextInt(8)+1
    	int firstTimeOffset = "3${my1stSecond}" as int
    	int secondTimeOffset = "3${my2ndSecond}" as int
		log.debug("line 395 state.delayPollData '${state.delayPollData}'")
        def i = rnd.next(1)
		int delaySeconds = i % 2
        runIn((delaySeconds==0) ? firstTimeOffset : secondTimeOffset , syncLocationData)
    

}


Map syncLocationData(data) {

startTimerAlerts()

 log.debug ("line 1761 syncLocationData() ${new Date()} string? ${(data instanceof String)} deviceid ${data} Map? ${(data instanceof Map)}")
 log.debug("line 1762 state.myAlertData '${state.myAlertData}'    state.myLocations  ${state.myLocations} ")
//logger("line 397 ${new Date()} Flume SM pollSLAlert() called ${deviceid}","info")

def deviceid = (data instanceof String) ? data : data.deviceID


//logger("line 1768 ${new Date()} syncLocationData() called","info")

state.Flume_location = state.myLocations.get(0).myDeviceID
    //log.debug("line 341 pollSLAlert() called ")
    //logger("Flume SM pollSLAlert() called","trace")
     //def idToString = (state.Flume_location?.id).toString()
  //  def existingDevice = getChildDevice(state.flumeDeviceId)//idToString)
    //state.myLocations
	if (state.Flume_location==deviceid){
	//log.debug("initFlume_locations(flumeUserId) = ${flumeUserId}")
   
   // logger("Flume SM syncLocationData() called","info")
    /*
        def qs = new groovy.json.JsonOutput().toJson(
        	[
                "user": false,
                "location": true
             ])
    log.debug("qs ${qs}")*/
    def params = [
            uri:  getApiBase(),
			path: "users/${state.flumeUserId}/devices",  
            query: [
           		 "user": false,
                "location": true //false //true
            ],
            headers: getDefaultAuthHeaders()
            ]
            /*
    state.Flume_location = null
	state.flumeDeviceId = null
    	*/
    	
     def tempLocationName = null
     def tempLocationDeviceId = null
     
     /*
     def tempLocationBattery = null
     def tempLocationTimeZone = null
     def tempLocationConnectedStatus = null
     
       def devicesList = []
       def batteryLevelList = []
       def deviceLocationName = []
       def deviceTimeZoneList = []
       def deviceConectedStatus = []
       
      def myLocations =[]
      */
      
    //   if (true){  //state.delaySyncData==false){   //state.lastSyncSuccess==false){
//log.debug("params ${params}")
	try {
    
        httpGet(params) {resp ->
            ////log.debug("resp.size() === ${resp.size()} ") 
            def resp_data = resp.data
          //  def Flume_locations1 = resp_data.data[1]
          //  log.debug("resp '${resp}'")
           // log.debug("Flume_locations1 '${Flume_locations1}'")
            log.debug("line 1828 syncLocationData() resp_data '${resp_data}'")
            // /* might Nov 28 need to delete
            def Flume_locations0 = resp_data.data[0]
            
			//state.flumeDeviceId = Flume_locations0.id
            //Nov 28 need to delete Flume_locations useless
			log.debug("line 1834 Flume_locations0 '${Flume_locations0}'")
			//log.debug("line 627 flumeDeviceId '${state.flumeDeviceId}'")
           // log.debug("Flume_locations0.location '${Flume_locations0.location}'")
			//log.debug("Flume_locations0.location.name '${Flume_locations0.location.name}'")
           // log.debug("resp.data.data.location '${resp.data.data.location}'")
           //   might Nov 28 need to delete            */
            def ttl = resp_data.count
            /*
            def mymap = [name:"Gromit", id:1234]
def x = mymap.find{ it.key == "likes" }?.value
if(x)
    println "x value: ${x}"

println x.getClass().name
            */
            
            log.debug("line 1754 resp_data.count '${ttl}'")
    
    		//resp.data.each{SL_loc->
            def counter = 0
          
             resp.data.data.each{SL_loc->
     			counter++
           	//	tempLocationName = SL_loc.location.name
            
            /*
            def emptyList = []
assert emptyList.size() == 0
emptyList.add(5) */

//return ["todayFlow":state.todayFlow, "thisMonthFlow":state.thisMonthFlow, 
//      "thisYearFlow":state.thisYearFlow,  /*"homeAway":state.homeAway, */ "inAlert":state.inAlert, "thisCurrentMinFlow":state.thisCurrentMinFlow]


                if(SL_loc.battery_level?.value)
                {
                
                state.lastSyncSuccess=true
                
               	tempLocationDeviceId = SL_loc.find{it.key=='id'}?.value
                
                def deviceCounter = state.myLocations.size()
               def nameIndex = 0
                log.debug("line 1877 syncLocationData() state.myLocations size '${deviceCounter}'    '${state.myLocations}'")
               for (def i = 0; i <deviceCounter; i++) {
               
                 if(tempLocationDeviceId==state.myLocations.get(i).myDeviceID)
                {
                    
                state.myLocations.get(i).myLocationName=SL_loc.location.name
                state.myLocations.get(i).myDeviceID=SL_loc.id
                state.myLocations.get(i).myDeviceBatteryLevel=SL_loc.battery_level
                state.myLocations.get(i).myBridgeConnectStatus=SL_loc.connected
                state.myLocations.get(i).myDeviceTimeZone=SL_loc.location.tz
                
                
                }//end of templocation=deviceID
                 
              //     state.myLocations.add("myLocationName": SL_loc.location.name, "myDeviceID": SL_loc.id, "myDeviceBatteryLevel":SL_loc.battery_level, "myBridgeConnectStatus": SL_loc.connected, "myDeviceTimeZone":SL_loc.location.tz )
                }//end of for loop
                
                }//end of battery data found
                
               // tempLocationBattery += SL_loc.battery_level?.value
               // batteryLevelList.
             //   tempLocationTimeZone = SL_loc.location.tz
                
                //log.debug("counter ${counter} line 643 tempLocationName /*'${tempLocationName}' */  tempDeviceID '${tempLocationDeviceId} ")
                 log.debug("counter ${counter} line 1071 tempDeviceID '${tempLocationDeviceId}'       deviceList '${devicesList}'        deviceLocationName '${deviceLocationName}'   deviceTimeZoneList   '${deviceTimeZoneList} ")  //, "warn")
                //state.flumeDeviceId = tempLocationDeviceId                    //Flume_locations0.id
             //   log.debug("tempDeviceID '${tempLocationDeviceId}")
                //log.debug("tempLocationBattery '${tempLocationBattery}'")
               
               /*
               state.myTZ = deviceTimeZoneList
                state.myBatterLevel = batteryLevelList 
				state.myConnectedSatus = deviceConectedStatus
                
               log.debug("line 811 state.timeZone = deviceTimeZoneList '${state.myTZ}"   )
         		*/
              //  log.debug ("line 651 true or false ${tempLocationName.equalsIgnoreCase(userFlume_locName)}")
              /*
             //   if ( /*tempLocationName*/ // SL_loc.location.name.equalsIgnoreCase(userFlume_locName)==true) { //Let user enter without worrying about case
             //        state.Flume_location = SL_loc  //resp.data.data[myCounter]//SL_loc //all data resp
              //       state.flumeDeviceId = SL_loc.id
              //       log.debug("inside line 654 SL_loc.location.name ${SL_loc.location.name}    state.flumeDeviceId ${state.flumeDeviceId}      \n SL_loc  ${SL_loc}")


					}//end for each location
    
    				            logger("line 1828 ${new Date()} syncData successfully called","info")
                
               //   */
            }//end response
            
            /*
            
            for (def i = 0; i <deviceTimeZoneList.size(); i++) {
             
            if(!tempLocationTimeZone.equals(state.myTZ.get(i))){
            	state.sameTZ = false
                break
                }
            }
           
            
            state.sameBatteryLevel = true
           state.sameConnectedStatus = true
           
           */
      		//state.myLocations = myLocations
            log.debug("line 1849 state.timeZone.get(0) '${state.myTZ.get(0)}"   )
                //logger("outside line 657 state.Flume_location ${state.Flume_location}", "warn")
             //log.debug("final loop count value ${myCounter} ")
          
          
          //if (!state.Flume_location){         //!tempLocationName){  //!state.Flume_location) {
		 //   	logger("Flume SM in initFlume_locations- Flume location name: ${userFlume_locName} not found!","warn")
        //    }// else {
            
			/*
			 def params = [
            uri:  getApiBase(),
			path: "users/${flumeUserId}/devices",         
			headers: getDefaultAuthHeaders(),
			]
			try {
        httpGet(params) {resp ->
		def resp_dataDevices = resp.data
		
		}*/
			
			
            
            ////log.debug("else condition existing Device")
            	//save current homeAway status
                //state.homeAway = state.Flume_location.homeAway 
                
                //create device handler for this location (device)
                //def idToString = (state.Flume_location.id).toString()
                
                //state.timeZone = deviceTimeZoneList.get(0)
                              
     //  } 
    } catch (e) {
		logger("line 1883 Flume SM error in syncLocationData retrieving locations: ${e}","error")
        state.delaySyncData = true
        
    }
    
      
               
               /*
               for (def i = 0; i <devicesCount; i++) {
			//log(" if(state.myLocations.get(i).myDeviceID==Flume_locations0.id)  ${(state.myLocations.get(i).myDeviceID==Flume_locations0.id)} ")                // "my i value '${i}'      respDeviceID  '${Flume_locations0.id}'      existingDevice '${state.myLocations.get(i)}'    ")
                if(state.myLocations.get(i).myDeviceID==Flume_locations0.id)
                {

                	state.myLocations.get(i).myBridgeConnectStatus = Flume_locations0.connected
                   state.myLocations.get(i).myLocationName 		= Flume_locations0.location.name
                 //  				= SL_loc.id
                   state.myLocations.get(i).myDeviceBatteryLevel	= Flume_locations0.battery_level 
                   state.myLocations.get(i).myDeviceTimeZone 		= Flume_locations0.location.tz 
                }
            }*/

   // }//end lasySync condition==false if (true)
     
    } 
    
    
    log.debug("line 2003 state.myLocations ${state.myLocations}")
    	/*
        if(state.delaySyncData){
  		
        Random rnd = new Random()
   		int my1stSecond = rnd.nextInt(4)+1
    	int my2ndSecond = rnd.nextInt(7)+1
    	int firstTimeOffset = "4${my1stSecond}" as int
    	int secondTimeOffset = "3${my2ndSecond}" as int
		log.debug("line 1343 state.delayPollData '${state.delayPollData}'")
        def i = rnd.next(1)
		int delaySeconds = i % 2
        runIn((delaySeconds==0) ? firstTimeOffset : secondTimeOffset , delaySync)
 		}
        */
    //runOnce(new Date(),printTest)
}// end of syncLocationData

def existingFlumeDataSync(deviceid){
	logger("line 1364 ${new Date()} existingFlumeDataSync() called","info")
	log.debug("line 1354 state.myLocations ${state.myLocations}")
    
    def deviceCounter = state.myLocations.size()
               def nameIndex = 0
                log.debug("line 1359 state.myLocations size '${deviceCounter}'    '${state.myLocations}'")
               for (def i = 0; i <deviceCounter; i++) {
               
                 if(state.myLocations.get(i).myDeviceID==deviceid)
                {
                
                return ["myBridgeConnectStatus":state.myLocations.get(i).myBridgeConnectStatus, "myLocationName":state.myLocations.get(i).myLocationName, "myDeviceBatteryLevel":state.myLocations.get(i).myDeviceBatteryLevel,
                	     "myDeviceTimeZone":state.myLocations.get(i).myDeviceTimeZone, "thisDeviceID":deviceid, "retrieveSyncDataResults":state.lastSyncSuccess, "delaySyncResults":state.delaySyncData ]
                
         
                //	state.myLocations.get(i).myBridgeConnectStatus// = Flume_locations0.connected
               //    state.myLocations.get(i).myLocationName// 		= Flume_locations0.location.name
                 //  				= SL_loc.id
                //   state.myLocations.get(i).myDeviceBatteryLevel//	= Flume_locations0.battery_level 
                 //  state.myLocations.get(i).myDeviceTimeZone// 		= Flume_locations0.location.tz 
                }//end of id matches deviceid
               
               }//end of loop device data
}//end of existingFlumeDataSync

def existingFlumeDevice(deviceid){

log.debug("line 873 before state.flumeUserId '${flumeUserId}'   deviceid '${deviceid}' ")
// logger("start existingFlumeDevice '${deviceid}'")
  def existingDevice = getChildDevice(deviceid)      ////it.myDeviceID)//    it.deviceNetworkId            idToString)   //state.Flume_location.locationId)
              //   //log.debug("line 416 existingDevice getChildDevice state.Flume_location.locationId=== ${state.Flume_location.get('id')}") //state.Flume_location.locationId}")
               if(existingDevice) {
               
               
               log.debug("line 880 after state.flumeUserId '${flumeUserId}'   deviceid '${deviceid}' ")
               
               //log.debug("initFlume_locations(flumeUserId) = ${flumeUserId}")
    logger("existingFlumeDevice existingDevice true called","trace")
    /*
        def qs = new groovy.json.JsonOutput().toJson(
        	[
                "user": false,
                "location": true
             ])
    log.debug("qs ${qs}")*/
    //https://api.flumewater.com/users/user_id/devices/device_id
    def params = [
            uri:  getApiBase(),
			path: "users/${flumeUserId}/devices/${deviceid}",  
            query: [
           		 "user": false,
                "location": true //false //true
            ],
            headers: getDefaultAuthHeaders()
            ]
            /*
    state.Flume_location = null
	state.flumeDeviceId = null
    	
     def tempLocationName = null
     def tempLocationDeviceId = null
     def tempLocationBattery = null
     def tempLocationTimeZone = null
     def tempLocationConnectedStatus = null
     
       def devicesList = []
       def batteryLevelList = []
       def deviceLocationName = []
       def deviceTimeZoneList = []
       def deviceConectedStatus = []
       */
      // def myLocations =[]
def Flume_locations0 = null  
def devicesCount = state.myLocations.size()
log.debug("line 943 state.myLocations contains begin '${state.myLocations}' end")
log.debug("line 920 params ${params}")
	try {
    
        httpGet(params) {resp ->
            ////log.debug("resp.size() === ${resp.size()} ") 
            def resp_data = resp.data
          //  def Flume_locations1 = resp_data.data[1]
          //  log.debug("resp '${resp}'")
           // log.debug("Flume_locations1 '${Flume_locations1}'")
            log.debug("line 925 existingFlumeDevice(deviceid) resp_data '${resp_data}'")
            // /* might Nov 28 need to delete
           Flume_locations0 = resp_data.data[0]
			//state.flumeDeviceId = Flume_locations0.id
            //Nov 28 need to delete Flume_locations useless
			log.debug("line 930 deviceid   ${deviceid}     Flume_locations0 '${Flume_locations0}'")
			//log.debug("line 627 flumeDeviceId '${state.flumeDeviceId}'")
           // log.debug("Flume_locations0.location '${Flume_locations0.location}'")
			//log.debug("Flume_locations0.location.name '${Flume_locations0.location.name}'")
           // log.debug("resp.data.data.location '${resp.data.data.location}'")
           //   might Nov 28 need to delete            */
            def ttl = resp_data.count
            /*
            def mymap = [name:"Gromit", id:1234]
def x = mymap.find{ it.key == "likes" }?.value
if(x)
    println "x value: ${x}"

println x.getClass().name
            */
            
            log.debug("line 945 resp_data.count '${ttl}'")
    
    		//resp.data.each{SL_loc->
         //   def counter = 0
          
       //      resp.data.data.each{SL_loc->
     	//		counter++
           	//	tempLocationName = SL_loc.location.name
            
            /*
            def emptyList = []
assert emptyList.size() == 0
emptyList.add(5) */
/*
       for (def i = 0; i <deviceTimeZoneList.size(); i++) {
             
            if(!tempLocationTimeZone.equals(state.myTZ.get(i))){
            	state.sameTZ = false
                break
                }
            }
            
            */

               // tempLocationBattery += SL_loc.battery_level?.value
               // batteryLevelList.
             //   tempLocationTimeZone = SL_loc.location.tz
                
                //log.debug("counter ${counter} line 643 tempLocationName /*'${tempLocationName}' */  tempDeviceID '${tempLocationDeviceId} ")
                // log.debug(" line 977 '${state.myLocations}'")  //, "warn")
                //state.flumeDeviceId = tempLocationDeviceId                    //Flume_locations0.id
             //   log.debug("tempDeviceID '${tempLocationDeviceId}")
                //log.debug("tempLocationBattery '${tempLocationBattery}'")
                
              
              //  log.debug ("line 651 true or false ${tempLocationName.equalsIgnoreCase(userFlume_locName)}")
              /*
             //   if ( /*tempLocationName*/ // SL_loc.location.name.equalsIgnoreCase(userFlume_locName)==true) { //Let user enter without worrying about case
             //        state.Flume_location = SL_loc  //resp.data.data[myCounter]//SL_loc //all data resp
              //       state.flumeDeviceId = SL_loc.id
              //       log.debug("inside line 654 SL_loc.location.name ${SL_loc.location.name}    state.flumeDeviceId ${state.flumeDeviceId}      \n SL_loc  ${SL_loc}")


			//		}//end for each location
    
    
                
               //   */
            }   //end response
            
                        
            log.debug("line 972 Flume_locations0.connected '${Flume_locations0.connected}'")
            
//return ["todayFlow":state.todayFlow, "thisMonthFlow":state.thisMonthFlow, 
//      "thisYearFlow":state.thisYearFlow,  /*"homeAway":state.homeAway, */ "inAlert":state.inAlert, "thisCurrentMinFlow":state.thisCurrentMinFlow]
	devicesCount = state.myLocations.size()
    log.debug("devicesCount  '${devicesCount}'")
	  for (def i = 0; i <devicesCount; i++) {
			//log(" if(state.myLocations.get(i).myDeviceID==Flume_locations0.id)  ${(state.myLocations.get(i).myDeviceID==Flume_locations0.id)} ")                // "my i value '${i}'      respDeviceID  '${Flume_locations0.id}'      existingDevice '${state.myLocations.get(i)}'    ")
                if(state.myLocations.get(i).myDeviceID==Flume_locations0.id)
                {


		


                	state.myLocations.get(i).myBridgeConnectStatus = Flume_locations0.connected
                   state.myLocations.get(i).myLocationName 		= Flume_locations0.location.name
                 //  				= SL_loc.id
                   state.myLocations.get(i).myDeviceBatteryLevel	= Flume_locations0.battery_level 
                   state.myLocations.get(i).myDeviceTimeZone 		= Flume_locations0.location.tz 
                }
            }
            
            
            
            /*
            for (def i = 0; i <deviceTimeZoneList.size(); i++) {
             
            if(!tempLocationTimeZone.equals(state.myTZ.get(i))){
            	state.sameTZ = false
                break
                }
            }
           */
            /*
            state.sameBatteryLevel = true
           state.sameConnectedStatus = true
      		*/
        //state.myLocations = myLocations
           // log.debug("line 737 state.timeZone.get(0) '${state.myTZ.get(0)}"   )
                //logger("outside line 657 state.Flume_location ${state.Flume_location}", "warn")
             //log.debug("final loop count value ${myCounter} ")
          
          
          //if (!state.Flume_location){         //!tempLocationName){  //!state.Flume_location) {
		 //   	logger("Flume SM in initFlume_locations- Flume location name: ${userFlume_locName} not found!","warn")
        //    }// else {
            
			/*
			 def params = [
            uri:  getApiBase(),
			path: "users/${flumeUserId}/devices",         
			headers: getDefaultAuthHeaders(),
			]
			try {
        httpGet(params) {resp ->
		def resp_dataDevices = resp.data
		
		}*/
			
			
            
            ////log.debug("else condition existing Device")
            	//save current homeAway status
                //state.homeAway = state.Flume_location.homeAway 
                
                //create device handler for this location (device)
                //def idToString = (state.Flume_location.id).toString()
                
                //state.timeZone = deviceTimeZoneList.get(0)
             //  def deviceCounter = 0
             //  def nameIndex = 0
               // log.debug("line 1058 existingdevice state.myLocations '${state.myLocations}'     size: ${state.myLocations.size()}'")
             
     //  } 
    } catch (e) {
		logger(" line 1064 existingFlumeDevice retrieving locations: $e","error")
    } 
               
   // log.debug("line 1067 existingFlumeDevice state.myLocations '${state.myLocations}'", "info")           
              
               }//end if existing loop 

/*
logger("Flume SM deleteSmartLabsDevice() called with deviceid: ${deviceid}","trace")
    def Flume_Devices = getChildDevices()
    
    Flume_Devices?.each {
    	if (it.deviceNetworkId == deviceid) {
			logger("Flume device exist deviceNetworkID: ${it.deviceNetworkId}","info")
  
            
            try {
                //deleteChildDevice(it.deviceNetworkId)
                //sendEvent(name: "DeviceDelete", value: "${it.deviceNetworkId} deleted")
            }
            catch (e) {
				logger("Flume SM deleteSmartLabsDevice- caught and ignored deleting child device: {it.deviceNetworkId} during cleanup: $e","info")
            }
            
        }
    }//end for FlumeDevices?.each  */

} //end of existingDevice(deviceid)




//Method to set Flume homeAway status; called with 'home' or 'away'
/*
def updateAway(newHomeAway) {
	logger("Flume SM updateAway() called with newHomeAway: ${newHomeAway}","trace")
    def cmdBody = [
			"homeAway": newHomeAway
	]
    def params = [
            uri:  'https://api.Flumewater.com/v1/locations/' + state.Flume_location.locationId,
            headers: ['Authorization': 'Bearer ' + appSettings.api_key],
            contentType: 'application/json',
			body : new groovy.json.JsonBuilder(cmdBody).toString()    
            ]

	logger("Flume SM updateAway params: ${params}","info")

	try {
        httpPutJson(params){resp ->
			logger("Flume SM updateAway resp data: ${resp.data}","info")
			logger("Flume SM updateAway resp status: ${resp.status}","info")
            if (resp.status == 200){//successful change
                def eventData = [name: "homeAway", value: newHomeAway]
                def existingDevice = getChildDevice(state.Flume_location?.locationId)
                existingDevice?.generateEvent(eventData) //tell child device new home/away status
                state.homeAway = newHomeAway
           }
        }
    } catch (e) {
		logger("Flume SM error in updateAway: $e","error")
    }
}
*/

//handler for when SmartThings mode changes
//if new mode is one of the ones specified for a Flume away mode, change Flume to away
//Do nothing if the user hasn't selected any modes defined as being Flume away.

/* def modeChangeHandler(evt) {
	logger("Flume SM modeChangeHandler() called by SmartThings mode changing to: ${evt.value}","info")
    ////log.debug "Flume SM SmartThings mode changed to ${evt.value}"
    ////log.debug "Flume_awayModes: ${Flume_awayModes}"
    ////log.debug "location.currentMode: ${location.currentMode}"
	def foundmode = false
	logger("Flume SM modeChangeHandler- user specified Flume_awayModes: ${Flume_awayModes}; # of modes: ${Flume_awayModes?.size}","debug")
    if (Flume_awayModes?.size() > 0) {//only do something if user specified some modes
        Flume_awayModes?.each{ awayModes->
            if (location.currentMode == awayModes) {
                foundmode = true //new mode is one to set Flume to away
            }
        }
        if (foundmode) {
            //change to away
			logger("Flume SM modeChangeHandler- changing Flume to away","info")
            updateAway("away")
        } else {
            //change to home; new mode isn't one specified for Flume away
			logger("Flume SM modeChangeHandler- changing Flume to home","info")
            updateAway("home")
        }
    }
} */

// Child called methods

// return current flow totals, etc.
Map retrievecloudData(deviceid) {
	logger("Flume SM retrievecloudData() called","trace")
    //get latest data from cloud
    /* determinehomeAway() */
  //Feb5 determineFlows(deviceid)
   //pollSLAlert()
   
   determineDeviceFlow(deviceid)//it.deviceNetworkId)
   
   //existingFlumeDevice(deviceid)
	//return ["todayFlow":state.todayFlow, "thisMonthFlow":state.thisMonthFlow, 
    //  "thisYearFlow":state.thisYearFlow,  /*"homeAway":state.homeAway, */ "inAlert":state.inAlert, "thisCurrentMinFlow":state.thisCurrentMinFlow, "thisDeviceID":deviceid, "retrieveDataResults":state.lastFlowDataSuccess]
	
	//state.myQueryData.get(j) = [currentMin:0.0, last24Hours:0, lastHour:0, myDeviceID:6715067545444607421,
    //retrieveWaterFlowDataResults:true, thisMonth:3284.8087698, thisYear:3284.8087698, today:110.81761146, waterFlowDelayResults:false]
 //  log.debug("line 1623 myWaterFlow   ${myWaterFlow}")
   log.debug("line 2129 state.myQueryData ${state.myQueryData}")
   
		for(def j=0; j<state.myQueryData.size(); j++){

				if(state.myQueryData.get(j).myDeviceID==deviceid){ //&&  state.myQueryData.get(j).retrieveWaterFlowDataResults==true){
                log.debug("line 2134 state.myQueryData.get(j).myDeviceID = ${state.myQueryData.get(j).myDeviceID}")
				return 	["thisCurrentMinFlow":state.myQueryData.get(j).currentMin, "last24Hours":state.myQueryData.get(j).last24Hours, "lastHour":state.myQueryData.get(j).lastHour,
                		"thisDeviceID":state.myQueryData.get(j).myDeviceID,"retrieveWaterFlowDataResults":state.myQueryData.get(j).retrieveWaterFlowDataResults,
                        "thisMonthFlow":state.myQueryData.get(j).thisMonth, "thisYearFlow":state.myQueryData.get(j).thisYear, "todayFlow":state.myQueryData.get(j).today,
                        "waterFlowDelayResults":state.myQueryData.get(j).waterFlowDelayResults, "inAlert":state.myAlertData.get(j).inAlertMode]          //state.inAlert]
																					//state.myLocations.get(i).inAlert								//state.myQueryData.get(j)
				}//end of if
             }//end of for loop
 
   	
    
   //existingFlumeDevice(deviceid)

	//return ["todayFlow":myWaterFlow.today, "thisMonthFlow":myWaterFlow.thisMonth, 
     // "thisYearFlow":myWaterFlow.thisYear,  /*"homeAway":state.homeAway, */ "inAlert":state.inAlert,
     // "thisCurrentMinFlow":myWaterFlow.currentMin, "thisDeviceID":myWaterFlow.myDeviceID, "retrieveDataResults":myWaterFlow.retrieveWaterFlowDataResults]

}

//delete child device; called by child device to remove itself. Seems unnecessary but documentation says to do this
def	deleteSmartLabsDevice(deviceid) {
	logger("Flume SM deleteSmartLabsDevice() called with deviceid: ${deviceid}","trace")
    def Flume_Devices = getChildDevices()
    Flume_Devices?.each {
    	if (it.deviceNetworkId == deviceid) {
			logger("Flume SM deleteSmartLabsDevice- deleting SL deviceNetworkID: ${it.deviceNetworkId}","info")
            try {
                deleteChildDevice(it.deviceNetworkId)
                sendEvent(name: "DeviceDelete", value: "${it.deviceNetworkId} deleted")
            }
            catch (e) {
				logger("Flume SM deleteSmartLabsDevice- caught and ignored deleting child device: {it.deviceNetworkId} during cleanup: $e","info")
            }
        }
    }
}

/**
 *  logger()
 *
 *  Wrapper function for all logging. Thanks codersaur.
 **/
private logger(msg, level = "debug") {

    switch(level) {
        case "error":
            if (state.loggingLevelIDE >= 1) log.error msg
            break

        case "warn":
            if (state.loggingLevelIDE >= 2) log.warn msg
            break

        case "info":
            if (state.loggingLevelIDE >= 3) log.info msg
            break

        case "debug":
            if (state.loggingLevelIDE >= 4) //log.debug msg
            break

        case "trace":
            if (state.loggingLevelIDE >= 5) log.trace msg
            break

        default:
            //log.debug msg
            break
    }
}
