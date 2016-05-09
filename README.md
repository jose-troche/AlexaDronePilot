# Alexa Drone Pilot

###Objective
To pilot a quadcopter drone with Alexa voice commands in real time

###Voice Interaction
_Alexa start drone pilot... take off_  -> The drone takes off

_Go up_  -> The drone flies up

_Picture_ -> The drone takes a picture

... other commands ...

_Land_  -> The drone lands

###A Cloud Multi-Service Solution

Alexa captures voice commands and sends them to the Alexa Service to convert/map them into structured text commands (JSON intents). A recognized command is sent to an AWS Lambda function that publishes the command to an AWS IoT topic to update the drone state (shadow).

On the other side of the world, a mobile application subscribes to the drone topic and listens for commands. The application connects securely to IoT using the preferred mqtt protocol and a key and a certificate obtained on first connection via Cognito. Every time a command is received, the mobile application sends the command to the drone. All this happens in real time.

Because the Internet of Things service supports multiple subscribers, a whole fleet of drones can be controlled with a single voice command. Furthermore, the drones could be replaced by other types of robots or machines.

![Architecture](https://raw.githubusercontent.com/jose-troche/Documentation/master/AlexaDronePilot/AlexaDronePilotArchitecture.png)

