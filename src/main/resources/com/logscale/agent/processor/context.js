Function.prototype.consumerize = function() {
  var func = this;
  return new java.util.function.Consumer({
    accept: func.bind(func)
  });
};
