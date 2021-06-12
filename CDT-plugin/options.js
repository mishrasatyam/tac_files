var session;
var password;
var username;
chrome.storage.sync.get(['sess','password','username'], function(data){ 
  session = data.sess;
  password = data.password;
  username = data.username;
});

document.addEventListener('DOMContentLoaded', function() {
  btn = document.querySelector('.wallet input[type="submit"]');
  btn.addEventListener('click', function() {
    value = document.querySelector('.wallet input[type="text"]').value;
    chrome.storage.sync.set({'wallet': value}, function() {});
  });
  
  inputs = document.querySelectorAll('.card input');
  inputs.forEach(function(listInput) {
    listInput.addEventListener('change', function() {
      if (typeof session === "undefined") {
        console.log('not defined');
      } else {
        if (this.checked) { 
          varId = this.dataset.id;
          var site = 'site'+varId;
          var save = {};
          save[site] = 'true';
          chrome.storage.sync.set(save)
        } else { 
          varId = this.dataset.id;
          var site = 'site'+varId;
          chrome.storage.sync.remove(site);
        }
      }
    });
  });
});

chrome.storage.sync.get(['wallet'], function(data){ 
  document.querySelector('.wallet input[type="text"]').value = data.wallet;
});

chrome.storage.sync.get(null, function(items) {
    var allKeys = Object.keys(items);
    allKeys.forEach(function(key) {
      if (key.substring(0, 4) == 'site') { 
        keyNum = key.slice(4);
        input = document.querySelector('.card input[data-id="'+keyNum+'"]');
        input.checked = true;
      }
    });
});