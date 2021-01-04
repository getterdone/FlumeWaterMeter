#OneApp FlumeWaterMeter
OneApp Flume Water Meter Smartthings Smartapp + Flume Water Meter Device Handler

1.  You need to get your API Client key and Secret Key from Flume's Customer Portal https://portal.flumetech.com/
See this help guide on steps: https://flumetech.readme.io/reference#accessing-the-api

2. Once app is installed in Smartthings web IDE you will need to provide Client Key and Secret Key in the Flume Smartapp>Edit SmartApp>settings: Add your Client and Secret info here.
3. Don't forget to do enable Oauth Flume Smartapp>Edit SmartApp>OAuth

4. Install/Add the Flume Smart App using your Smartthings Mobile OneApp App: One App smartthings: From dashboard Click the + , In the 'Add' screen select 'SmartApp' then find the smartapp 'OneApp Flume Water Flow SM' 

5. Once you enter your login credentials in the Flume Options smartthings app click Done this new OneApp implementation will find your Flume devices and assigned the names automatically based on the names you provided on the Flume ios/android app. So if your Flume device name is Flume-Location Home1, and Flume-Location Home2, in the OneApp Smartthings app dashboard, there should be two new sensor devices "Flume-Location Home1-Flume Meter", and "Flume-Location Home2-Flume Meter". You only need one SM instance to manage multiple Flume devices. This will handle Low Leak Alerts. 

7. Enjoy



Acknowledgements: My first Smartthings app and groovy stab, want to thank windsurfer99 for his implementation:
https://community.smartthings.com/t/release-streamlabs-smart-home-water-meter-interface/166769

also thank joshs85
https://community.smartthings.com/t/release-honeywell-lyric-water-leak-sensor-connect/109003

also thank michaeldavie
https://community.home-assistant.io/t/flume-water-meter/124485/42

and this one not sure who the author is
https://www.reddit.com/r/homesecurity/comments/5f98l5/smartthings_and_scout_alarm_integration/

I ended up using bits of this and that, some custom tweaks here and there, and after a lot of log.debug statements using web Smartthings IDE that at times provides zero stack tracing I created this Flume frankenstein app...Again enjoy..
First github contribution too woohoo, more to come, stay tuned.
