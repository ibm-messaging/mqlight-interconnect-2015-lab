/*******************************************************************************
 * Copyright (c) 2014 IBM Corporation and other Contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html 
 *
 * Contributors:
 * IBM - Initial Contribution
 *******************************************************************************/

// PASTE SNIPPET 1 HERE
  
var http = require('http');
var express = require('express');
var fs = require('fs');
var bodyParser = require('body-parser');

// PASTE SNIPPET 2 HERE

// PASTE SNIPPET 9 HERE

// PASTE SNIPPET 3 HERE

/*
 * Establish HTTP credentials, then configure Express
 */
var httpOpts = {};
httpOpts.port = (process.env.VCAP_APP_PORT || 3000);

var app = express();

/*
 * Store a maximum of one message from the MQ Light server, for the browser to poll. 
 * The polling GET REST handler does the confirm
 */
var heldMsgs = new Array();

/*
 * Add static HTTP content handling
 */
function staticContentHandler(req,res) {
  var url = req.url.substr(1);
  if (url == '') { url = __dirname + '/index.html';};
  if (url == 'style.css') {res.contentType('text/css');}
  fs.readFile(url,
  function (err, data) {
    if (err) {
      res.writeHead(404);
      return res.end('Not found');
    }
    res.writeHead(200);
    return res.end(data);
  });
}
app.all('/', staticContentHandler);
app.all('/*.html', staticContentHandler);
app.all('/*.css', staticContentHandler);
app.all('/images/*', staticContentHandler);

/*
 * Use JSON for our REST payloads
 */
app.use(bodyParser.json());

/*
 * POST handler to publish words to our topic
 */
app.post('/rest/words', function(req,res) {
  
  // Check they've sent { "words" : "Some Sentence" }
  if (!req.body.words) {
    res.writeHead(500);
    return res.end('No words');
  }
  // Split it up into words
  var msgCount = 0; 
  req.body.words.split(" ").forEach(function(word) {
    // Send it as a message
    var msgData = {
      "word" : word,
      "frontend" : "Node.js"
    };
    console.log("Sending message: " + JSON.stringify(msgData));

    processMessage(msgData);  // REPLACE THIS LINE WITH SNIPPET 4
    msgCount++; 
  });
  // Send back a count of messages sent
  res.json({"msgCount" : msgCount});
});

/*
 * GET handler to poll for notifications
 */
app.get('/rest/wordsuppercase', function(req,res) {
  // Do we have a message held?
  var msg = heldMsgs.pop();
  if (msg) {
    // Send the data to the caller
    res.json(JSON.parse(msg.data));
  }
  else {
    // Just return no-data
    res.writeHead(204);
    res.end();
  }
  sleep(100);
});

/*
 * Start our REST server
 */
if (httpOpts.host) {
  http.createServer(app).listen(httpOpts.host, httpOpts.port, function () {
    console.log('App listening on ' + httpOpts.host + ':' + httpOpts.port);
  });
}
else {
  http.createServer(app).listen(httpOpts.port, function () {
    console.log('App listening on *:' + httpOpts.port);
  });
}

/*
 * Handle a word sent from the web page
 */
function processMessage(data) {
  var word = data.word;
  try {
    // Convert JSON into an Object we can work with 
    data = JSON.parse(data);
    word = data.word;
  } catch (e) {
    // Expected if we already have a Javascript object
  }
  if (!word) {
    console.error("Bad data received: " + data);
  }
  else {
    console.log("Received data: " + JSON.stringify(data));
    sleep(500); // This blocks the node worker thread for 1 second
    // you would normally never do this. We are doing it to _simulate_
    // a complex algorithm that takes a long time to run. 

    // Upper case it and publish a notification
    var replyData = {
        "word" : word.toUpperCase(),
        "backend" : "Node.js"
    };
    // Convert to JSON to give the same behaviour as Java
    // We could leave as an Object, but this is better for interop
    replyData = JSON.stringify(replyData);
    console.log("Sending response: " + replyData);
    heldMsgs.push({"data" : replyData});
  }
}

function sleep(time) {
  var end = new Date().getTime();
  while(new Date().getTime() < end + time) {;}
}
