// SNIPPET 1
var PUBLISH_TOPIC = "mqlight/sample/words";
var SUBSCRIBE_TOPIC = "mqlight/sample/wordsuppercase";

// SNIPPET 2
var mqlight = require('mqlight');
var opts = {};
opts.service = 'amqp://localhost:5672';

// SNIPPET 3
var mqlightClient = mqlight.createClient(opts, function(err) {
  if (err) { 
    console.error('Error ' + err);
  } else {
    console.log('Connected with id ' + mqlightClient.id);
  }
});

// SNIPPET 4
mqlightClient.send(PUBLISH_TOPIC, msgData);

// SNIPPET 5
var mqlight = require('mqlight');
var opts = {};
opts.service = 'amqp://localhost:5672';

// SNIPPET 6
var SUBSCRIBE_TOPIC = "mqlight/sample/words";
var PUBLISH_TOPIC = "mqlight/sample/wordsuppercase";

// SNIPPET 7
var mqlightClient = mqlight.createClient(opts, function(err) {
  if (err) {
    console.err('Error: ' + err);
  } else {
    console.log('Connected');
  }
  mqlightClient.on('message', processMessage);
  mqlightClient.subscribe(SUBSCRIBE_TOPIC, function(err) {
    if (err) console.err("Failed to subscribe: " + err); 
    else { console.log("Subscribed"); }
  });
});

// SNIPPET 8
mqlightClient.send(PUBLISH_TOPIC, replyData);


// SNIPPET 9
function handleProcessedMessage(msg) {
  heldMsgs.push({"data" : msg});
}

// SNIPPET 10
var mqlightClient = mqlight.createClient(opts, function(err) {
  if (err) { 
    console.error('Error ' + err);
  } else {
    console.log('Connected with id ' + mqlightClient.id);
  }

  mqlightClient.on('message', handleProcessedMessage);
  mqlightClient.subscribe(SUBSCRIBE_TOPIC , function(err) {
    if (err) console.err("Failed to subscribe: " + err); 
    else { console.log("Subscribed"); }
  });
});
