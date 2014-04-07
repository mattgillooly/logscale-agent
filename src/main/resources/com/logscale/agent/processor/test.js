// demonstrates staged and complex event processing

var source = "my-source";
var eventBuffer = new Packages.com.logscale.agent.util.PushStream(1000);
var sequence = 1;

new Packages.com.logscale.agent.engine.Processor({
  events: function() {
    return eventBuffer;
  },

  handler: function() {
    return (function(event) {
      if (event.source === source) {
        return;
      }
      var event2 = new Packages.com.logscale.agent.event.Event(source, event.timestamp, sequence++, source + ": " + event.content);
      eventBuffer.accept(event2);
    }).consumerize();
  }
});
