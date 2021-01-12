/**
 *  OneApp Flume Water Flow SM
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
    name: "OneApp Flume Water Flow SM",
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
    
    state.myLocations = []
    
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
     def Flume_Devices = getChildDevices()
     log.debug("line 129 Flume_Devices '${Flume_Devices}'")
    Flume_Devices?.each {
    	//if (it.deviceNetworkId == deviceid) {
    
	state.flumeDeviceId  = it.deviceNetworkId //getflumeDeviceId()
    state.Flume_location = null
    state.childDevice = null
    state.inAlert = false
    
    /* state.homeAway = "home" */
    subscribe(location, "mode"/* , modeChangeHandler */)
   //Dec 21 initFlume_locations(flumeUserId) //determine Flume location to use
    log.debug("line 128 initialize()FLOW state.Flume_location = ${state.Flume_location}")
    
    if ( state.flumeDeviceId ) { //state.Flume_location){          //state.flumeDeviceId) {   //state.Flume_location) { 
         log.debug("we have a device; put it into initial state")      
        def eventData = [name: "water", value: "dry"]
        //log.debug("state.Flume_location?.id ===${state.Flume_location?.id}")
          //def idToString = (state.Flume_location?.id).toString()
          log.debug("inside initialize state.flumeDeviceId '${state.flumeDeviceId}'")
        def existingDevice = getChildDevice(state.flumeDeviceId)//idToString)              
        existingDevice?.generateEvent(eventData) //this was off the hold time?? now back on not sure what is happening
        state.inAlert =  false
       // Nov 16 2020  
   //    schedule("0 0/3 * * * ?", pollSLAlert) //Poll Flume cloud for leak alert //change 0 0/3 to 4  0/4 for four minutes 0/3 Dec3
        runIn(8,"initDevice") //update once things are initialized
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
		}//end of rep
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
}

//Handler for schedule; determine if there are any alerts

def pollSLAlert() {
	state.Flume_location = state.myLocations
    log.debug("line 286 pollSLAlert() called ")
    //logger("Flume SM pollSLAlert() called","trace")
     //def idToString = (state.Flume_location?.id).toString()
    def existingDevice = getChildDevice(state.flumeDeviceId)//idToString)
    //state.myLocations
	if (state.Flume_location){
     log.debug ("begin 305: state.Flume_location true" )
       def params = [
            uri:  getApiBase(),
			path: "users/${flumeUserId}/notifications",         
			headers: getDefaultAuthHeaders(),
           // query: [read: "false"],
    ]
        try {
            httpGet(params) {resp ->
    			logger("Flume SM pollSLAlert resp.data: ${resp.data}","debug")
				
				
                def resp_data = resp.data
              
			  
				if (resp.status == 200){//successful retrieve
                log.debug ("begin: pollSLAlert() resp_data line 272 begin ${resp_data} end")
               
                	def myLowWaterFlowAlert = null
                    def myLowWaterFlowAlertMessage = null
                    def Flume_locationsAlert 
                   resp_data.data.message.each{ tempMessage->
                   log.debug (" tempMessage '${tempMessage}'")
                   if(tempMessage.contains("Low Flow Leak")){
                   myLowWaterFlowAlertMessage = tempMessage //"Low Water Alert True"
                   myLowWaterFlowAlert = true
                   Flume_locationsAlert = "Low Flow Leak"
                   			}//Low Flow Leak condition
                   
                   }// end of for eachMessage Loop
               
				 log.debug ("resp_data line 277 begin '${myLowWaterFlowAlertMessage}' end ")
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
				
				}
                    else{
                     log.debug("fail httpPostJson(params)")
                    }
				
				
            }// end of resp
        } catch (e) {
    		logger("line 304 Flume SM pollSLAlert error retrieving alerts: '${e}'","error")
        }
    }
}


//callback in order to initialize device
def initDevice() {
	//log.debug("line 327 '${deviceid}'")
    logger("Flume SM initDevice() called","trace")
    
    
    def Flume_Devices = getChildDevices()
     log.debug("line 332 Flume_Devices '${Flume_Devices}'")
    Flume_Devices?.each {
    	//if (it.deviceNetworkId == deviceid) {
    
	determineFlows(it.deviceNetworkId)
    /* determinehomeAway() */
    //def idToString = (state.Flume_location?.id).toString()
  def existingDevice = getChildDevice(it.deviceNetworkId)    //state.flumeDeviceId)//idToString)//idToString)
 existingDevice?.refresh()
 }
}

/*def now = new Date()
def date = new Date()
sdf = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss")
println sdf.format(date)//log.debug(now.format("yyyyMMdd-HH:mm:ss.SSS", TimeZone.getTimeZone('UTC')))
*/
//"2016-04-04 01:00:00",

//determine flow totals from cloud
def determineFlows(deviceid) {
log.debug("line 356 determineFlows'${deviceid}'")
def lastMinute = null
def today = null
def lastHour = null
def last24Hours = null 
def adjustedDate = null
def adjustedTime = null
def tz = null
log.debug("line 373 myTimeZone ${getmyTimeZone()}    state.timeZone.get(0) '${state.myTZ.get(0)}'")   //state.timeZone.get(0)
def tempDeviceTZ = null

state.myLocations?.each{
	
	if(it.myDeviceID==deviceid){
    	log.debug("**determineFlows:locations state.myLocations.myDeviceTimeZone** sameTZ= '${it.myDeviceTimeZone}'")
	tempDeviceTZ = it.myDeviceTimeZone
		}
	}
 
//tempDeviceTZ = state.myTZ.get(0)

log.debug("tempDeviceTZ '${tempDeviceTZ}'")

use (groovy.time.TimeCategory) {


	//go here to find your timeZone http://www.java2s.com/Code/Java/Development-Class/DisplayAvailableTimeZones.htm and enter it in line 341
//def myTimeZone = state.timeZone.get(0)//state.timeZone.get(0)
tz = TimeZone.getTimeZone(tempDeviceTZ)     //"America/New_York") //(TimeZone.getDefault())
log.debug("line 381 tz '${tz}'")
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
    def existingDevice = getChildDevice(deviceid) //state.flumeDeviceId) //idToString) //need to do the ? to see what it does
  	log.debug("line 457 determineFlows(): state.flumeDeviceId '${deviceid}'") //state.flumeDeviceId}'")
	if (existingDevice){
       def params = [
            uri:  getApiBase(),
			path: "users/${flumeUserId}/devices/${deviceid}/query",      //state.flumeDeviceId}/query",         
			headers: getDefaultAuthHeaders(),
            body: body   //bodyString
    ]
    log.debug("try params output'${params}'")
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
				 
				    }
                    else{
                     log.debug("fail httpPostJson(params)")
                    }
                
            } //end of response
        } catch (e) {
    		logger("Flume SM determineFlows error retrieving summary data e= ${e}","error")
            
            /* disabled Nov 162020: debug PollAlert needed
            state.thisCurrentMinFlow = 0
            state.todayFlow = 0
            state.thisMonthFlow = 0
            state.thisYearFlow = 0
            */
            //state.unitsFlow = "gallons"
            
        }
    }//end of existing device
    
    
    
    
}//end of determine flows
 
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
	//log.debug("initFlume_locations(flumeUserId) = ${flumeUserId}")
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
    
    	
    
        httpGet(params) {resp ->
            ////log.debug("resp.size() === ${resp.size()} ") 
            def resp_data = resp.data
          //  def Flume_locations1 = resp_data.data[1]
          //  log.debug("resp '${resp}'")
           // log.debug("Flume_locations1 '${Flume_locations1}'")
            log.debug("line 615 resp_data '${resp_data}'")
            // /* might Nov 28 need to delete
            def Flume_locations0 = resp_data.data[0]
			//state.flumeDeviceId = Flume_locations0.id
            //Nov 28 need to delete Flume_locations useless
			log.debug("line 626 Flume_locations0 '${Flume_locations0}'")
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
                 log.debug("counter ${counter} line 710 tempDeviceID '${tempLocationDeviceId}'       deviceList '${devicesList}'        deviceLocationName '${deviceLocationName}'   deviceTimeZoneList   '${deviceTimeZoneList} ")  //, "warn")
                //state.flumeDeviceId = tempLocationDeviceId                    //Flume_locations0.id
             //   log.debug("tempDeviceID '${tempLocationDeviceId}")
                //log.debug("tempLocationBattery '${tempLocationBattery}'")
                state.myTZ = deviceTimeZoneList
                state.myBatterLevel = batteryLevelList 
				state.myConnectedSatus = deviceConectedStatus
                
               log.debug("line 811 state.timeZone = deviceTimeZoneList '${state.myTZ}"   )
         		
              //  log.debug ("line 651 true or false ${tempLocationName.equalsIgnoreCase(userFlume_locName)}")
              /*
             //   if ( /*tempLocationName*/ // SL_loc.location.name.equalsIgnoreCase(userFlume_locName)==true) { //Let user enter without worrying about case
             //        state.Flume_location = SL_loc  //resp.data.data[myCounter]//SL_loc //all data resp
              //       state.flumeDeviceId = SL_loc.id
              //       log.debug("inside line 654 SL_loc.location.name ${SL_loc.location.name}    state.flumeDeviceId ${state.flumeDeviceId}      \n SL_loc  ${SL_loc}")


					}//end for each location
    
    
                
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
            log.debug("line 737 state.timeZone.get(0) '${state.myTZ.get(0)}"   )
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
                log.debug("state.myLocations '${state.myLocations}'")
               state.myLocations?.each{
                 
            	
                log.debug("${deviceCounter++} it '${it.myDeviceID}'")
              
              def existingDevice = getChildDevice(it.myDeviceID)//                idToString)   //state.Flume_location.locationId)
              //   //log.debug("line 416 existingDevice getChildDevice state.Flume_location.locationId=== ${state.Flume_location.get('id')}") //state.Flume_location.locationId}")
               if(!existingDevice) {
               
                // //log.debug("line 418 !existingDevice === ${existingDevice}")
              //  log.debug("line 681 state.flumeDeviceId ${state.flumeDeviceId}   tempLocationDeviceId ${tempLocationDeviceId}")
               def childDevice = addChildDevice("getterdone", "OneApp Flume Water Flow DH",   it.myDeviceID , null, 
               [name: "${it.myLocationName}-Flume Meter", label: " ${it.myLocationName/*deviceLocationName.get(nameIndex)*/}-Flume Meter" /*"#${deviceCounter}-Flume Meter" */ , completedSetup: true])
               nameIndex= nameIndex+1
                  // def childDevice = addChildDevice("getterdone", "Flume Water Flow DH", state.flumeDeviceId /*idToString*/, null, [name: "Flume Water Flow DH", label: "Flume Water - ${}" , completedSetup: true])
		    	//	logger("Flume SM initFlume_locations- device created with Id: ${state.Flume_location.get('id')/*locationId*/} for Flume_location: ${state.Flume_location.get('name')/*name*/}","info")
					////log.debug("Flume SM initFlume_locations- device created with Id: ${state.Flume_location.get('id')/*locationId*/} for Flume_location: ${state.Flume_location.get('name')/*name*/} ") 
             } else {
		    		logger("Flume SM initFlume_locations- device not created; already exists: ${existingDevice.getDeviceNetworkId()}","warn")
             
             
             } 
                
            }//end of myLocations loop
     //  } 
    } catch (e) {
		logger(" line 863 Flume SM error in initFlume_locations retrieving locations: $e","error")
    }

    
}


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
   determineFlows(deviceid)
   //pollSLAlert()
   //existingFlumeDevice(deviceid)
	return ["todayFlow":state.todayFlow, "thisMonthFlow":state.thisMonthFlow, 
      "thisYearFlow":state.thisYearFlow,  /*"homeAway":state.homeAway, */ "inAlert":state.inAlert, "thisCurrentMinFlow":state.thisCurrentMinFlow]
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