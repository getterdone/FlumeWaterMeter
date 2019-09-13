//working Flume DH


/**
 *  Flume Water Flow DH
 *  Device Handler for StreamLabs Water Flow Meter: Cloud Connected Device; created by StreamLabs Water Flow Service Manager/ SmartApp
 *  Version 1.0
 *
 *  Copyright 2019 getterdone
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
	definition (name: "Flume Water Flow DH", namespace: "getterdone", author: "Ulices Soriano", cstHandler: true) {
		capability "Water Sensor"
        capability "Sensor"
        capability "Health Check"
        capability "refresh"
     
     /* command "changeToAway"
        command "changeToHome"
        */
     
     command "changeToPause"
        command "changeToMonitor"
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
			state "dry", icon:"st.alarm.water.dry", backgroundColor:"#ffffff"
			state "wet", icon:"st.alarm.water.wet", backgroundColor:"#00A0DC"
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
		
        details(["water", /*"homeAway",*/ "suspend", "refresh", "todayFlow", "monthFlow", "yearFlow"])
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
	log.debug "StreamLabs DH installed() called"

	runIn(7,"initialize")
}

def initialize() {
    // Logging Level:
    state.loggingLevelIDE = (settings.zwtLoggingLevelIDE) ? settings.zwtLoggingLevelIDE.toInteger() : 3

	log.debug "StreamLabs DH initialize() called with pause timeout: ${pauseDelay}"
    schedule("0 0/6 * * * ?", poll) //refresh every 10 minutes changed to 6 
	generateEvent([name:"suspend", value:"monitor"])
    state.wetDry = "dry" //real status even if paused
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

    logger("StreamLabs DH updated() called","trace")
    unschedule("poll")
    runIn(7,"initialize")
}

// parse events into attributes; not really used with this type of Device Handler
def parse(String description) {
    logger("StreamLabs DH iparse called with '${description}","trace")

}
//poll for changes and update locally; framework is supposed to call every 10 minutes but doesn't seem to
//so scheduled internally, this is the handler for that
def poll() {
    logger("StreamLabs DH poll() called","trace")
    refresh()
}

def refresh(){
	def cloudData = parent.retrievecloudData() 
    logger("StreamLabs DH refresh() called- cloudData: ${cloudData}, suspend: ${device.currentValue("suspend")}","debug")
    
    state.todayFlow = cloudData.todayFlow
    state.thisMonthFlow = cloudData.thisMonthFlow
    log.debug("device refresh  state.thisMonthFlow '${state.thisMonthFlow}'")
    state.thisYearFlow = cloudData.thisYearFlow
    
    /*state.homeAway = cloudData.homeAway*/
    
    state.wetDry = cloudData.inAlert ? "wet" : "dry"
    if (device.currentValue("suspend") != "pause") { //update only if not paused
		generateEvent([name:"water", value:state.wetDry])
    }	
    
	sendEvent(name: "todayFlow", value: Math.round(cloudData.todayFlow))
	sendEvent(name: "monthFlow", value: Math.round(cloudData.thisMonthFlow))
	sendEvent(name: "yearFlow", value: Math.round(cloudData.thisYearFlow))
    /*
	sendEvent(name: "homeAway", value: cloudData.homeAway)*/
}

//actions

/* //Tile action to change StreamLabs to home
def changeToHome() {
    logger("StreamLabs DH changeToHome() called","debug")
	parent.updateAway("home")
}
 */

/* //Tile action to change StreamLabs to away
def changeToAway() {
    logger("StreamLabs DH changeToAway() called","debug")
	parent.updateAway("away")
} */

//Tile action (& suspend time limit action) to re-enble monitoring StreamLabs for alerts
def changeToMonitor() {
    logger("StreamLabs DH changeToMonitor() called","debug")
	generateEvent([name:"suspend", value:"monitor"])
	generateEvent([name:"water", value:state.wetDry]) //update real status in case it had been suspended
}

//Tile action to pause monitoring StreamLabs for alerts
def changeToPause() {
    logger("StreamLabs DH changeToPause() called","debug")
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
    logger("StreamLabs DH generateEvent() called with parameters: '${results}'","debug")
	sendEvent(results)
	return null
}

//Typically called by parent: update to "wet"
def changeWaterToWet() {
	def suspension = device.currentValue("suspend")
    logger("StreamLabs DH changeWaterToWet() called with suspend: ${suspension}","info")
    state.wetDry = "wet"
    if (suspension != "pause") { //update tile displayed info only if not paused
		generateEvent([name:"water", value:"wet"])
	}
}

//Typically called by parent: update to "dry"
def changeWaterToDry() {
    logger("StreamLabs DH changeWaterToDry() called","info")
    state.wetDry = "dry"
    //update even if paused
	generateEvent([name:"water", value:"dry"])
}