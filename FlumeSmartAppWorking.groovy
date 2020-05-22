/**
 *  StreamLabs Water Flow SM
 *  Smart App/ Service Manager for StreamLabs Water Flow Meter
 *  This will create a companion Device Handler for the StreamLabs device
 *  Version 1.0
 *
 *  You MUST enter the API Key value via 'App Settings'->'Settings'->'api_key' in IDE by editing this SmartApp code
 *  This key is provided by StreamLabs upon request
 *
 *  Copyright 2019 Bruce Andrews, Ulices Soriano
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
    name: "Flume Water Flow SM",
    namespace: "getterdone",
    author: "Ulices Soriano",
    description: "Service Manager for cloud-based API for Flume Water Flow meter",
    category: "My Apps",
    iconUrl: "https://windsurfer99.github.io/ST_StreamLabs-Water-Flow/tap-water-icon-128.png",
    iconX2Url: "https://windsurfer99.github.io/ST_StreamLabs-Water-Flow/tap-water-icon-256.png",
    iconX3Url: "https://windsurfer99.github.io/ST_StreamLabs-Water-Flow/tap-water-icon-256.png",
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
            		input (name: "userFlume_locName", type: "text", title: "Enter Streamlabs location name assigned to Streamlabs flow meter", 
                    		multiple: false, required: true)
                    input (name: "configLoggingLevelIDE",
                        title: "IDE Live Logging Level:\nMessages with this level and higher will be logged to the IDE.",
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
	}
}

def getApiBase() { return "https://api.flumetech.com/" }

private String FlumeAPIKey() {return appSettings.FlumeAPI_Key}
private String FlumeAPISecret() {return appSettings.FlumeAPI_Secret}

//required methods
def installed() {
	//log.debug "StreamLabs SM installed with settings: ${settings}"
    state.enteredLocName =  userFlume_locName//save off the location name entered by user
    runIn(3, "initialize")
}

def updated() {
    if (state.enteredLocName != userFlume_locName) { //if location name changed, need to make a new device
    	logger("StreamLabs SM updated() called- new device with settings: ${settings}","trace")
        unsubscribe()
        cleanup()
    	runIn(10, "initialize") //deleteChildDevice seems to take a while to delete; wait before re-creating
    } else {
	   	state.loggingLevelIDE = (settings.configLoggingLevelIDE) ? settings.configLoggingLevelIDE.toInteger() : 3
		logger("StreamLabs SM updated() called- same name, no new device with settings: ${settings}","info")
    }
}

def initialize() {
    state.loggingLevelIDE = (settings.configLoggingLevelIDE) ? settings.configLoggingLevelIDE.toInteger() : 3
    logger("StreamLabs SM initialize() called with settings: ${settings}","trace")
	// get the value of api key
	def mySecret = FlumeAPISecret()  //appSettings.api_key
    if (mySecret.length() <20) {
    	logger("StreamLabs SM initialize- api_secret value not set properly in IDE: ${mySecret}","error")
    }
	state.flumeUserId = getflumeUserId()
	state.flumeDeviceId  = getflumeDeviceId()
    state.Flume_location = null
    state.childDevice = null
    state.inAlert = false
    /* state.homeAway = "home" */
    subscribe(location, "mode"/* , modeChangeHandler */)
    initFlume_locations(flumeUserId) //determine Streamlabs location to use
    log.debug("initialize()FLOW state.Flume_location = ${state.Flume_location}")
    
    if (state.Flume_location) { 
         log.debug("we have a device; put it into initial state")      
        def eventData = [name: "water", value: "dry"]
        //log.debug("state.Flume_location?.id ===${state.Flume_location?.id}")
          //def idToString = (state.Flume_location?.id).toString()
          log.debug("inside initialize state.flumeDeviceId '${state.flumeDeviceId}'")
        def existingDevice = getChildDevice(state.flumeDeviceId)//idToString)              
        existingDevice?.generateEvent(eventData) //this was off the hold time?? now back on not sure what is happening
        state.inAlert =  false
       schedule("0 0/3 * * * ?", pollSLAlert) //Poll Streamlabs cloud for leak alert //change 0 0/3 to 2  0/2 for two minutes
        runIn(8,"initDevice") //update once things are initialized
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
	return state.flumeDeviceId
}

def uninstalled() {
    logger("StreamLabs SM uninstalled() called","trace")
    cleanup()
}


//remove things
def cleanup() {
    logger("StreamLabs SM cleanup() called","trace")
    def Flume_Devices = getChildDevices()
    Flume_Devices.each {
    	logger("StreamLabs SM cleanup- deleting SL deviceNetworkID: ${it.deviceNetworkId}","info")
    	try {
            deleteChildDevice(it.deviceNetworkId)
        }
    	catch (e) {
    		logger("StreamLabs SM cleanup- caught and ignored deleting child device: {it.deviceNetworkId}: $e","info")
       	}
    }
    state.Flume_location = null
    state.childDevice = null
    state.inAlert = false
}

//Handler for schedule; determine if there are any alerts

def pollSLAlert() {
    logger("StreamLabs SM pollSLAlert() called","trace")
     //def idToString = (state.Flume_location?.id).toString()
    def existingDevice = getChildDevice(state.flumeDeviceId)//idToString)
	if (state.Flume_location){
       def params = [
            uri:  getApiBase(),
			path: "users/${flumeUserId}/notifications",         
			headers: getDefaultAuthHeaders(),
           // query: [read: "false"],
    ]
        try {
            httpGet(params) {resp ->
    			logger("StreamLabs SM pollSLAlert resp.data: ${resp.data}","debug")
                def resp_data = resp.data
                
                log.debug ("resp_data line 262 begin ${resp_data} end")
               
                	def myLowWaterFlowAlert = null
                    def myLowWaterFlowAlertMessage = null
                    def Flume_locationsAlert 
                   resp_data.data.message.each{ tempMessage->
                   log.debug (tempMessage)
                   if(tempMessage.contains("Low Flow Leak")){
                   myLowWaterFlowAlertMessage = tempMessage //"Low Water Alert True"
                   myLowWaterFlowAlert = true
                   Flume_locationsAlert = "Low Flow Leak"
                   }
                   
                   }
               
				 log.debug ("resp_data line 277 begin '${myLowWaterFlowAlertMessage}' end ")
              if (myLowWaterFlowAlert) {    
            //  if (true) {
                    //send wet event to child device handler every poll to ensure not lost due to handler pausing
    				logger("StreamLabs SM pollSLAlert Alert0 received: ${Flume_locationsAlert}; call changeWaterToWet","info")
                    existingDevice?.changeWaterToWet()
                    state.inAlert =  true
                } else {
                    if (state.inAlert){
                        //alert removed, send dry event to child device handler only once
    					logger("StreamLabs SM pollSLAlert Alert0 deactivated ; call changeWaterToDry","info")
                        existingDevice?.changeWaterToDry()
                        state.inAlert =  false
                    }
                }
            }
        } catch (e) {
    		logger("StreamLabs SM pollSLAlert error retrieving alerts: $e","error")
        }
    }
}


//callback in order to initialize device
def initDevice() {
    logger("StreamLabs SM initDevice() called","trace")
	determineFlows()
    /* determinehomeAway() */
    //def idToString = (state.Flume_location?.id).toString()
  def existingDevice = getChildDevice(state.flumeDeviceId)//idToString)//idToString)
 existingDevice?.refresh()
}


/*def now = new Date()
def date = new Date()
sdf = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss")
println sdf.format(date)//log.debug(now.format("yyyyMMdd-HH:mm:ss.SSS", TimeZone.getTimeZone('UTC')))
*/
//"2016-04-04 01:00:00",

//determine flow totals from cloud
def determineFlows() {
def lastMinute = null
def today = null
def lastHour = null
def last24Hours = null 
def adjustedDate = null
def adjustedTime = null
use (groovy.time.TimeCategory) {

	//go here to find yours http://www.java2s.com/Code/Java/Development-Class/DisplayAvailableTimeZones.htm
	//enter your timezone in line 340
def tz = TimeZone.getTimeZone("America/New_York") //(TimeZone.getDefault())
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
      
    logger("StreamLabs SM determineFlows() called","trace")
    //def idToString = (state.Flume_location?.id).toString()
    def existingDevice = getChildDevice(state.flumeDeviceId) //idToString) //need to do the ? to see what it does
  	log.debug("determineFlows(): state.flumeDeviceId '${state.flumeDeviceId}'")
	if (existingDevice){
       def params = [
            uri:  getApiBase(),
			path: "users/${flumeUserId}/devices/${state.flumeDeviceId}/query",         
			headers: getDefaultAuthHeaders(),
            body: body//bodyString
    ]
    //log.debug("try params output'${params}'")
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
                     }
                    else{
                     log.debug("fail httpPostJson(params)")
                    }
                    
                //this should have worked? log.debug("resp.status '${resp.status}'")
    			//logger("StreamLabs SM determineFlows resp.data: ${resp.data}","debug")
                
                //log.debug("outside of if success condition resp.data '${resp.data}'")
                //log.debug("device queris result '${resp_data}'")
           
            	//log.debug("resp_data?.data.today.value '${resp_data?.data.today.value}'")
                 //log.debug("resp_data.data.today.value '${resp_data.data.today.value}'")
              // def myWaterValues = resp_data.data.getAt('thisMonth')//.size()
             //  log.debug(" getAt('thisMonth') myWaterValues '${myWaterValues}'")
                    
                    
                   state.thisCurrentMinFlow = (resp_data.data.currentMin?.value[0][0]).toInteger() //!= null ?  (resp_data.data.currentMin.value[0][0]).toInteger() : 0  //(resp_data.data.getAt('currentMin').getAt('value')[0][0]).toInteger()  //resp_data.data.thisMonth.value
                   log.debug("state.thisCurrentMinFlow  '${state.thisCurrentMinFlow}'")
               
               
               
               state.todayFlow = (resp_data.data.today.value[0][0]).toInteger()
               log.debug("state.todayFlow '${ state.todayFlow}'")
               
               state.thisMonthFlow = (resp_data.data.getAt('thisMonth').getAt('value')[0][0]).toInteger()  //resp_data.data.thisMonth.value
                 log.debug("state.thisMonthFlow  '${state.thisMonthFlow }'")
               
               state.thisYearFlow = (resp_data.data.thisYear.value[0][0]).toDouble()
                 log.debug("state.thisYearFlow '${state.thisYearFlow}'")
                //state.unitsFlow = resp_data?.units
                
            }
        } catch (e) {
    		logger("StreamLabs SM determineFlows error retrieving summary data e= ${e}","error")
            
            state.thisCurrentMinFlow = 0
            state.todayFlow = 0
            state.thisMonthFlow = 0
            state.thisYearFlow = 0
            
            state.unitsFlow = "gallons"
            
        }
    }
}
 
//determine StreamLabs home/away from StreamLabs cloud

/* def determinehomeAway() {
    logger("StreamLabs SM determinehomeAway() called","trace")
    def existingDevice = getChildDevice(state.Flume_location?.locationId)
	if (existingDevice){
        def params = [
                uri:  'https://api.streamlabswater.com/v1/locations/' + state.Flume_location.locationId,
                headers: ['Authorization': 'Bearer ' + appSettings.api_key],
                contentType: 'application/json',
                ]
		try {
            httpGet(params) {resp ->
    			logger("StreamLabs SM determinehomeAway resp.data: ${resp.data}; resp.status: ${resp.status}","debug")
                if (resp.status == 200){//successful retrieve
                    def resp_data = resp.data
                    state.homeAway = resp_data?.homeAway
                }
            }
        } catch (e) {
    		logger("StreamLabs SM determinehomeAway error: $e","error")
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
//Get desired location from Streamlabs cloud based on user's entered location's name
def initFlume_locations(flumeUserId) {
	//log.debug("initFlume_locations(flumeUserId) = ${flumeUserId}")
    logger("StreamLabs SM initFlume_locations() called","trace")
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
                "location": true
            ],
            headers: getDefaultAuthHeaders()
            ]
    state.Flume_location = null
	state.flumeDeviceId = null
log.debug("params ${params}")
	try {
        httpGet(params) {resp ->
            ////log.debug("resp.size() === ${resp.size()} ") 
            def resp_data = resp.data
          //  def Flume_locations1 = resp_data.data[1]
          //  log.debug("resp '${resp}'")
           // log.debug("Flume_locations1 '${Flume_locations1}'")
           // log.debug("resp_data '${resp_data}'")
            def Flume_locations0 = resp_data.data[0]
			state.flumeDeviceId = Flume_locations0.id
			log.debug("Flume_locations0 '${Flume_locations0}'")
			log.debug("flumeDeviceId '${state.flumeDeviceId}'")
           // log.debug("Flume_locations0.location '${Flume_locations0.location}'")
			log.debug("Flume_locations0.location.name '${Flume_locations0.location.name}'")
           // log.debug("resp.data.data.location '${resp.data.data.location}'")
            
            def ttl = resp_data.count
    
             resp.data.data.each{SL_loc->
     
           		def tempLocationName = SL_loc.location.name
                log.debug("tempLocationName '${tempLocationName}'")
         		
                if (tempLocationName.equalsIgnoreCase(userFlume_locName)) { //Let user enter without worrying about case
                     state.Flume_location = SL_loc  //resp.data.data[myCounter]//SL_loc //all data resp
                }
            }
            
             //log.debug("final loop count value ${myCounter} ")
            if (!state.Flume_location) {
		    	logger("StreamLabs SM in initFlume_locations- StreamLabs location name: ${userFlume_locName} not found!","warn")
            } else {
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
                def existingDevice = getChildDevice(state.flumeDeviceId)//                idToString)   //state.Flume_location.locationId)
              //   //log.debug("line 416 existingDevice getChildDevice state.Flume_location.locationId=== ${state.Flume_location.get('id')}") //state.Flume_location.locationId}")
                if(!existingDevice) {
                // //log.debug("line 418 !existingDevice === ${existingDevice}")
                   def childDevice = addChildDevice("getterdone", "Flume Water Flow DH", state.flumeDeviceId /*idToString*/, null, [name: "Flume Water Flow DH", 
                        label: "Flume Water Flow DH", completedSetup: true])
		    		//logger("StreamLabs SM initFlume_locations- device created with Id: ${state.Flume_location.get('id')/*locationId*/} for Flume_location: ${state.Flume_location.get('name')/*name*/}","info")
					////log.debug("StreamLabs SM initFlume_locations- device created with Id: ${state.Flume_location.get('id')/*locationId*/} for Flume_location: ${state.Flume_location.get('name')/*name*/} ") 
                } else {
		    		logger("StreamLabs SM initFlume_locations- device not created; already exists: ${existingDevice.getDeviceNetworkId()}","warn")
                }
            }
       } 
    } catch (e) {
		logger("StreamLabs SM error in initFlume_locations retrieving locations: $e","error")
    }
}

//Method to set Streamlabs homeAway status; called with 'home' or 'away'
/*
def updateAway(newHomeAway) {
	logger("StreamLabs SM updateAway() called with newHomeAway: ${newHomeAway}","trace")
    def cmdBody = [
			"homeAway": newHomeAway
	]
    def params = [
            uri:  'https://api.streamlabswater.com/v1/locations/' + state.Flume_location.locationId,
            headers: ['Authorization': 'Bearer ' + appSettings.api_key],
            contentType: 'application/json',
			body : new groovy.json.JsonBuilder(cmdBody).toString()    
            ]

	logger("StreamLabs SM updateAway params: ${params}","info")

	try {
        httpPutJson(params){resp ->
			logger("StreamLabs SM updateAway resp data: ${resp.data}","info")
			logger("StreamLabs SM updateAway resp status: ${resp.status}","info")
            if (resp.status == 200){//successful change
                def eventData = [name: "homeAway", value: newHomeAway]
                def existingDevice = getChildDevice(state.Flume_location?.locationId)
                existingDevice?.generateEvent(eventData) //tell child device new home/away status
                state.homeAway = newHomeAway
           }
        }
    } catch (e) {
		logger("StreamLabs SM error in updateAway: $e","error")
    }
}
*/

//handler for when SmartThings mode changes
//if new mode is one of the ones specified for a StreamLabs away mode, change Streamlabs to away
//Do nothing if the user hasn't selected any modes defined as being Streamlabs away.

/* def modeChangeHandler(evt) {
	logger("StreamLabs SM modeChangeHandler() called by SmartThings mode changing to: ${evt.value}","info")
    ////log.debug "StreamLabs SM SmartThings mode changed to ${evt.value}"
    ////log.debug "Flume_awayModes: ${Flume_awayModes}"
    ////log.debug "location.currentMode: ${location.currentMode}"
	def foundmode = false
	logger("StreamLabs SM modeChangeHandler- user specified Flume_awayModes: ${Flume_awayModes}; # of modes: ${Flume_awayModes?.size}","debug")
    if (Flume_awayModes?.size() > 0) {//only do something if user specified some modes
        Flume_awayModes?.each{ awayModes->
            if (location.currentMode == awayModes) {
                foundmode = true //new mode is one to set Streamlabs to away
            }
        }
        if (foundmode) {
            //change to away
			logger("StreamLabs SM modeChangeHandler- changing StreamLabs to away","info")
            updateAway("away")
        } else {
            //change to home; new mode isn't one specified for Streamlabs away
			logger("StreamLabs SM modeChangeHandler- changing StreamLabs to home","info")
            updateAway("home")
        }
    }
} */

// Child called methods

// return current flow totals, etc.
Map retrievecloudData() {
	logger("StreamLabs SM retrievecloudData() called","trace")
    //get latest data from cloud
    /* determinehomeAway() */
   determineFlows()
   pollSLAlert()
	return ["todayFlow":state.todayFlow, "thisMonthFlow":state.thisMonthFlow, 
      "thisYearFlow":state.thisYearFlow,  /*"homeAway":state.homeAway, */ "inAlert":state.inAlert, "thisCurrentMinFlow":state.thisCurrentMinFlow]
}

//delete child device; called by child device to remove itself. Seems unnecessary but documentation says to do this
def	deleteSmartLabsDevice(deviceid) {
	logger("StreamLabs SM deleteSmartLabsDevice() called with deviceid: ${deviceid}","trace")
    def Flume_Devices = getChildDevices()
    Flume_Devices?.each {
    	if (it.deviceNetworkId == deviceid) {
			logger("StreamLabs SM deleteSmartLabsDevice- deleting SL deviceNetworkID: ${it.deviceNetworkId}","info")
            try {
                deleteChildDevice(it.deviceNetworkId)
                sendEvent(name: "DeviceDelete", value: "${it.deviceNetworkId} deleted")
            }
            catch (e) {
				logger("StreamLabs SM deleteSmartLabsDevice- caught and ignored deleting child device: {it.deviceNetworkId} during cleanup: $e","info")
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
