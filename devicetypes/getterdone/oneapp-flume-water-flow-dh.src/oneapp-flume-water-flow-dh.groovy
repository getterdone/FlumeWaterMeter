/** 
 *  OneApp Flume Water Flow DH
 *  Device Handler for Flume Water Flow Meter: Cloud Connected Device; created by Flume Water Flow Service Manager/ SmartApp
 *  Version 2.0 OneApp
 *
 *  Copyright 2021 windsurfer99, getterdone
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 */
metadata {
	definition (name: "OneApp Flume Water Flow DH", namespace: "getterdone", author: "Ulices Soriano",   mnmn: "SmartThingsCommunity", vid: "d665a967-eccd-3ac7-bb72-b3d4c626f4dd", cstHandler: true) {
		capability "Water Sensor"   
        capability "Sensor"
        capability "Health Check"
        capability "refresh"
     
  		//added this
        capability "mediapepper07880.watermeterflow"
        capability "mediapepper07880.watermetermonitorstatus"

     
     /* command "changeToAway"
        command "changeToHome"
        */
     
    	command "changeToPause"
        command "changeToMonitor"
        
        attribute "batteryLevel", "string"
        attribute "connectedStatus", "string"
        
        attribute "thisCurrentMinFlow", "string"
        attribute "todayFlow", "string"
        attribute "monthFlow", "string"
        attribute "yearFlow", "string"
        attribute "suspend", "enum", ["pause", "monitor"] //tracks if user has requested to suspend leak alerts
       /* attribute "homeAway", "enum", ["home", "away"] */
	}


	simulator {
		// TODO: define status and reply messages here
	}
    
    
    

	tiles (scale: 2)  {
		standardTile("water", "device.water", width: 6, height: 4) {
			state "dry", icon:"st.alarm.water.dry", backgroundColor:"#ffffff", label: '${currentValue}'
			state "wet", icon:"st.alarm.water.wet", backgroundColor:"#00A0DC", label: '${currentValue} GPM'
            
            
		}
		standardTile("refresh", "capability.refresh", decoration: "flat", width: 2, height: 2, canChangeIcon: true) {
			state "default", label:"Refresh", icon:"st.secondary.refresh", action:"refresh.refresh"
		}
		standardTile("suspend", "device.suspend", decoration: "flat", width: 2, height: 3) {
			state "pause", label: "Now paused", icon:"st.sonos.play-btn", action:"changeToMonitor"
			state "monitor", label: "Now monitoring", defaultState:true, icon:"st.sonos.pause-btn", action:"changeToPause"
		}
		
/* 		standardTile("homeAway", "device.homeAway", decoration: "flat", width: 2, height: 2, canChangeIcon: true) {
			state "home", icon:"st.nest.nest-home", defaultState:true, action:"changeToAway", backgroundColor:"#00A0DC", nextstate: "changingToAway"
			state "away", icon:"st.nest.nest-away", action:"changeToHome", backgroundColor:"#CCCCCC", nextstate: "changingToHome"
    		state "changingToAway", label:'Changing', icon:"st.nest.nest-away", backgroundColor:"#CCCCCC", nextState: "changingToHome"
    		state "changingToHome", label:'Changing', icon:"st.nest.nest-home", backgroundColor:"#00A0DC", nextState: "changingToAway"
		}
 */	
 
 		valueTile("thisCurrentMinFlow", "device.thisCurrentMinFlow", decoration: "flat", height: 2, width: 2) {
			state "thisCurrentMinFlow", label: 'Flow ${currentValue} Gal.'
		}
	
		valueTile("todayFlow", "device.todayFlow", decoration: "flat", height: 2, width: 2) {
			state "todayFlow", label: 'Today ${currentValue} Gal.'
		}

		valueTile("monthFlow", "device.monthFlow", decoration: "flat", height: 1, width: 2) {
			state "monthFlow", label: 'Month ${currentValue} Gal.'
		}
 
		valueTile("yearFlow", "device.yearFlow", decoration: "flat", height: 1, width: 2) {
			state "yearFlow", label: 'Year ${currentValue} Gal.'
		}
        
		main "water"
		
        details(["water", /*"homeAway",*/ "suspend", "refresh", "thisCurrentMinFlow", "todayFlow", "monthFlow", "yearFlow"])
    }
    preferences {
        input (name: "pauseDelay", type: "number", title: "# of min. for pause", description: "# of minutes for max. pause:", required: false)
        input (name: "zwtLoggingLevelIDE",
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
            required: false
        )
	}
}
//required implementations
def installed() {
	log.debug "Flume DH installed() called"

	runIn(7,"initialize")
}

def initialize() {
    // Logging Level:
    state.loggingLevelIDE = (settings.zwtLoggingLevelIDE) ? settings.zwtLoggingLevelIDE.toInteger() : 3
	log.debug "Flume DH initialize() called with pause timeout: ${pauseDelay}"
    
    //new
  //  runEvery5Minutes(parent.pollSLAlert())
  //  schedule("0 0/3 * * * ?", parent.pollSLAlert() ) 
    //new
  //  runEvery5Minutes(poll())
   //original line 
   schedule("0 0/2 * * * ?", poll) //refresh every 10 minutes change to 5
   
   //new
   //schedule("0 0/5 * * * ?", parent.retrievecloudData(device.deviceNetworkId))
   
   //new
   //runEvery15Minutes(parent.existingFlumeDevice(device.deviceNetworkId))
  // schedule("0 0/14 * * * ?", parent.existingFlumeDevice(device.deviceNetworkId) ) //schedule every 8 minutes



generateEvent([name:"suspend", value:"monitor"])
    state.wetDry = "dry" //real status even if paused

    
    //sendEvent(name: "listElement", value: "todayFlow")
    sendEvent(name: "waterMeterMonitorState", value: "monitoring")
}

/**
 *  updated()
 *
 *  Runs when the user hits "Done" from Settings page.
 *
 *  Note: Weirdly, update() seems to be called twice. tried solutions described here but didn't work
 *  (I'm guessing it is a re-entrance issue). See: https://community.smartthings.com/t/updated-being-called-twice/62912
 **/
def updated(){
    // Logging Level:
    state.loggingLevelIDE = (settings.zwtLoggingLevelIDE) ? settings.zwtLoggingLevelIDE.toInteger() : 3
	
    logger("Flume DH updated() called","trace")
    unschedule("poll")
    runIn(7,"initialize")
}

// parse events into attributes; not really used with this type of Device Handler
def parse(String description) {
    logger("Flume DH iparse called with '${description}","trace")

}
//poll for changes and update locally; framework is supposed to call every 10 minutes but doesn't seem to
//so scheduled internally, this is the handler for that
def poll() {
    logger("Flume DH poll() called","trace")
    refresh()
}


	def setPlaybackStatus(boolean argument){
     switch(argument) {
     case true:
     	log.debug("setPlaybackStatus argument true case: '${argument}'")
     	//changeToPause()
        break
     case false:
     	log.debug("setPlaybackStatus argument false case: '${argument}'")
     	//changeToMonitor()
        break         
     default:
             //addEvent(name: argument, value: argument)
             log.debug("setPlaybackStatus argument default case: '${argument}'")
            break    
     }
    
    }
    
    
    def setWaterMeterMonitorState(String argument){
     // log.debug("setWaterMeterMonitorState argument value: '${argument}'")
       switch(argument) {
        case "paused":
            log.debug("setWaterMeterMonitorState paused case argument value: '${argument.value}'")   //  + '${mediapepper07880.watermetermonitorstatus}' */   )
            changeToPause()
            sendEvent(name: "waterMeterMonitorState", value: "${argument.value}")  //"Now Paused")
            break
        case "monitoring":
        	 log.debug("setWaterMeterMonitorState monitoring case argument value: '${argument.value}'")
        	 changeToMonitor()
             sendEvent(name: "waterMeterMonitorState", value: "${argument.value}" ) //"Now Monitoring" )
             break
        default:
        	 log.debug("setWaterMeterMonitorState default case argument value: '${argument}'")
        	  break
        }
      
    }

    def setListElement(String argument) {
   //  refresh()
   
    def myTempCurrentMin =  String.format("%,.2f", (state.thisCurrentMinFlow).toDouble() ) //at this point null? Math.round(cloudData.thisCurrentMinFlow)  try this too: state.thisCurrentMinFlow.value
    def myTempTodayFlow = String.format("%,.2f", (state.todayFlow).toDouble() )
    def myTempMonthFlow = String.format("%,.2f", (state.thisMonthFlow).toDouble() )
    def myTempYearFlow =     String.format("%,.2f", (state.thisYearFlow).toDouble() )  
   
   /*
    def myTempCurrentMin =  state.thisCurrentMinFlow //at this point null? Math.round(cloudData.thisCurrentMinFlow)  try this too: state.thisCurrentMinFlow.value
    def myTempTodayFlow = state.todayFlow
    def myTempMonthFlow = state.thisMonthFlow
    def myTempYearFlow =     String.format("%,.2f", (state.thisYearFlow).toDouble() )                              //state.thisYearFlow
   */
   
   // log.debug("myTempCurrentMin value: '${myTempCurrentMin}'")
    //log.debug("argument value: '${argument}' ")
        //addEvent(name: "todayFlow", value: argument)
       			
          //  log.debug(" '${listElement}' setListElement argument state.argument.value : '${state.argument.value}'")
        //	sendEvent(name: "listElement", value: "${argument}")
    		//	sendEvent(name: "mediapepper07880.watermeterflow", value: "${argument}", isStateChange: true, displayed: true)

        /*
    sendEvent(name: "thisCurrentMinFlow", value: Math.round(cloudData.thisCurrentMinFlow))
	sendEvent(name: "todayFlow", value: Math.round(cloudData.todayFlow)   )
	sendEvent(name: "monthFlow", value: Math.round(cloudData.thisMonthFlow)      )
	sendEvent(name: "yearFlow", value: String.format("%.2f", (cloudData.thisYearFlow).toDouble() ) )   
        */
        
        switch(argument) {
        case "thisCurrentMinFlow":
            //addEvent(name: "thisCurrentMinFlow", value: "${thisCurrentMinFlow}" )
            log.debug("setListElement argument thisCurrentMinFlow case: '${argument}'")
               sendEvent(name: "listElement", value: "Currently: '${myTempCurrentMin}' GPM")
            // sendEvent(name: "listElement", value: "Currently: 27 Gallon(s)")
            break
        case "todayFlow":
           // addEvent(name: "todayFlow", value: "${state.todayFlow.value}" )
           log.debug("setListElement argument todayFlow case: '${argument}'")
           sendEvent(name: "listElement", value: "Today: '${myTempTodayFlow}' Gallon(s)")
         //   sendEvent(name: "listElement", value: "Today: 300 Gallon(s)")
            break
        case "monthFlow":
          //  addEvent(name: "monthFlow", value: "${monthFlow}" )
          log.debug("setListElement argument monthFlow case: '${argument}'")
             sendEvent(name: "listElement", value: "Monthly: '${myTempMonthFlow}' Gallon(s)")
           // sendEvent(name: "flowValue", value: "Monthly: 1000 Gallon(s)")
            break
        case "yearFlow":
          //  addEvent(name: "yearFlow", value: "${yearFlow}" )
          log.debug("setListElement argument yearFlow case: '${argument}'")
           sendEvent(name: "listElement", value: "Yearly: '${myTempYearFlow}' Gallon(s)")
           // sendEvent(name: "flowValue", value: "Yearly: 39000 Gallon(s)")
            break
        default:
             //addEvent(name: argument, value: argument)
             log.debug("setListElement argument default case: '${argument}'")
            break          
             }
	}//end of setListElement


def refresh(){
//log.debug("line 266 deviceid.deviceNetworkId '${device.deviceNetworkId}'")
	def cloudData = parent.retrievecloudData(device.deviceNetworkId)
    def anyAlerts = parent.pollSLAlert()
    def syncDevice = parent.existingFlumeDevice(device.deviceNetworkId)
    logger("Flume DH refresh() called- cloudData: ${cloudData}, suspend: ${device.currentValue("suspend")}","debug")
    
    
 // def myTempTodayFlow = state.todayFlow.value
 //  log.debug(${myTempTodayFlow})
    // logger("myTodayFlow: ${myTodayFlow}","info")
 //   setTodayFlow(myTempTodayFlow)
  
  
  	//    log.debug("device refresh  myTempTodayFlow '${myTempTodayFlow}'")
    state.thisCurrentMinFlow= cloudData.thisCurrentMinFlow
    state.todayFlow = cloudData.todayFlow
    state.thisMonthFlow = cloudData.thisMonthFlow
 
    //String.format("%.2f", d));
   // state.thisYearFlow = (cloudData.thisYearFlow)
 //  def typeOfData = cloudData.thisYearFlow
  // log.debug("typeOfData = ${typeOfData}")
    state.thisYearFlow =    (cloudData.thisYearFlow) //String.format("%.2f", (cloudData.thisYearFlow).toDouble() ) 
  //  state.thisYearFlow = String.format("%.2f", (cloudData.thisYearFlow) )//.round(1)  
     //  log.debug("device refresh  state.thisYearFlow '${ state.thisYearFlow}'")
    /*state.homeAway = cloudData.homeAway*/
    
    state.wetDry = cloudData.inAlert ? "wet" : "dry"
    if (device.currentValue("suspend") != "pause") { //update only if not paused
		generateEvent([name:"water", value:state.wetDry])
    }	
    
  //  setListElement(String argument)   ///, isStateChange: true, displayed: true
    
    /*
    generateEvent([name: "thisCurrentMinFlow", value: Math.round(cloudData.thisCurrentMinFlow) ])	
   // sendEvent(name: "thisCurrentMinFlow", value: Math.round(cloudData.thisCurrentMinFlow) )				// 	, isStateChange: true, displayed: true)
	sendEvent(name: "todayFlow", value: Math.round(cloudData.todayFlow) )                  				// 	, isStateChange: true, displayed: true)
	sendEvent(name: "monthFlow", value: Math.round(cloudData.thisMonthFlow)	)							//  , isStateChange: true, displayed: true)
	sendEvent(name: "yearFlow", value: String.format("%,.2f", (cloudData.thisYearFlow).toDouble() ))	//	, isStateChange: true, displayed: true)       //(cloudData.thisYearFlow)) //.round(1)     )
    /*
	sendEvent(name: "homeAway", value: cloudData.homeAway)*/


    def myTempCurrentMin =  String.format("%,.2f", (state.thisCurrentMinFlow).toDouble() ) //at this point null? Math.round(cloudData.thisCurrentMinFlow)  try this too: state.thisCurrentMinFlow.value
    def myTempTodayFlow = String.format("%,.2f", (state.todayFlow).toDouble() )
    def myTempMonthFlow = String.format("%,.2f", (state.thisMonthFlow).toDouble() )
    def myTempYearFlow =     String.format("%,.2f", (state.thisYearFlow).toDouble() )                              //state.thisYearFlow

		 // sendEvent(name: "listElement", value: "Currently R: '${myTempCurrentMin}' GPM")
                   
                   sendEvent(name: "listElement", value: "YearFlow R: '${myTempYearFlow}' Gallon(s)")
                   sendEvent(name: "listElement", value: "MonthFlow R: '${myTempMonthFlow}' Gallon(s)")
                   sendEvent(name: "listElement", value: "TodayFlow R: '${myTempTodayFlow}' Gallon(s)")




	} //end of Refresh

//actions

/* //Tile action to change Flume to home
def changeToHome() {
    logger("Flume DH changeToHome() called","debug")
	parent.updateAway("home")
}
 */


/* //Tile action to change Flume to away
def changeToAway() {
    logger("Flume DH changeToAway() called","debug")
	parent.updateAway("away")
} */

//Tile action (& suspend time limit action) to re-enble monitoring Flume for alerts
def changeToMonitor() {
    logger("Flume DH changeToMonitor() called","debug")
	generateEvent([name:"suspend", value:"monitor"])
	generateEvent([name:"water", value:state.wetDry]) //update real status in case it had been suspended
}

//Tile action to pause monitoring Flume for alerts
def changeToPause() {
    logger("Flume DH changeToPause() called","debug")
	generateEvent([name:"suspend", value:"pause"])
	generateEvent([name:"water", value:"dry"]) //remove wet while paused
    if (pauseDelay > 0) {//if user wants a time limit on suspending alerts
	    runIn (pauseDelay*60, "changeToMonitor")
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
            if (state.loggingLevelIDE >= 4) log.debug msg
            break

        case "trace":
            if (state.loggingLevelIDE >= 5) log.trace msg
            break

        default:
            log.debug msg
            break
    }
}
//handle Events sent from Service Manager; typically wet & dry
def generateEvent(Map results) {
    logger("Flume DH generateEvent() called with parameters: '${results}'","debug")
	sendEvent(results)
	return null
}

//Typically called by parent: update to "wet"
def changeWaterToWet() {
	def suspension = device.currentValue("suspend")
    logger("Flume DH changeWaterToWet() called with suspend: ${suspension}","info")
    state.wetDry = "wet"
    if (suspension != "pause") { //update tile displayed info only if not paused
		generateEvent([name:"water", value:"wet"])
	}
}

//Typically called by parent: update to "dry"
def changeWaterToDry() {
    logger("Flume DH changeWaterToDry() called","info")
    state.wetDry = "dry"
    //update even if paused
	generateEvent([name:"water", value:"dry"])
}