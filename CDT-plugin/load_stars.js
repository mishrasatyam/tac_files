var session;
var password;
var username;
  function loadJSON2(page, callback) {
    chrome.tabs.query({active: true,currentWindow: true}, function(tabs) {
      chrome.storage.sync.get(['sess','password','username'], function(data){ 
        session = data.sess;
        password = data.password;
        username = data.username;
      });
      atab = tabs[0];
      current_url = atab.url;
      var xhttp = new XMLHttpRequest();
      xhttp.overrideMimeType("application/json");
      xhttp.open('POST', 'https://network.tactokens.com/exchange/stars.php', true);
      xhttp.setRequestHeader("Content-type", "application/x-www-form-urlencoded");
      xhttp.setRequestHeader('x-data-method', 'form-post');
      xhttp.setRequestHeader('Cache-Control', 'no-cache');
      xhttp.send("site="+page+"&key="+session+"&password="+password+"&username="+username);
      xhttp.onreadystatechange = function () {
        if (xhttp.readyState == 4 && xhttp.status == "200") {
          callback(xhttp.responseText);
        }
      };  
    });
  }
  function lookupReview(page, callback) {
    chrome.tabs.query({active: true,currentWindow: true}, function(tabs) {
      chrome.storage.sync.get(['sess','password','username'], function(data){ 
        session = data.sess;
        password = data.password;
        username = data.username;
      });
      atab = tabs[0];
      current_url = atab.url;
      var xhttp = new XMLHttpRequest();
      xhttp.overrideMimeType("application/json");
      xhttp.open('POST', 'https://network.tactokens.com/exchange/lookup_review.php', true);
      xhttp.setRequestHeader("Content-type", "application/x-www-form-urlencoded");
      xhttp.setRequestHeader('x-data-method', 'form-post');
      xhttp.setRequestHeader('Cache-Control', 'no-cache');
      xhttp.send("site="+page+"&key="+session+"&password="+password+"&username="+username);
      xhttp.onreadystatechange = function () {
        if (xhttp.readyState == 4 && xhttp.status == "200") {
          callback(xhttp.responseText);
        }
      };  
    });
  }

  chrome.tabs.query({active: true,currentWindow: true}, function(tabs) {
    atab = tabs[0];
    page = atab.url;
    loadJSON2(page, function(response) {
      var elem = document.querySelector('#star > *');
      if(elem){ elem.parentNode.removeChild(elem); }
      var elem2 = document.querySelector('#star');
      var mySpan = document.createElement("div"); 
      var mySpan2 = document.createElement("div");
      if(elem2){ elem2.append(mySpan); }
      mySpan.append(mySpan2);
      mySpan.classList.toggle('starRatingContainer');
      mySpan2.classList.toggle('className');
      mySpan2.id = 'starClick';
      var properties1 = JSON.parse(response);
      if (properties1[0] !== undefined && properties1[0].rating !== undefined) {
        var className="className";
        rateSystem(className, properties1, function(rating, ratingTargetElement){ ratingTargetElement.parentElement.parentElement.parentElement.parentElement.parentElement.getElementsByClassName("className")[1].innerHTML = rating; });
        document.getElementById("ratingNum").innerHTML = properties1[0].rating;
        var starContainer = document.getElementById('starContainer');
        starContainer.style.display = 'block';
        var review = document.getElementById('review');
        if (properties1[0].rated == 1) { 
          var remove = document.querySelector('.rate');
          remove.style.display = 'none';
        } else {
          var remove = document.querySelector('.rate');
          remove.style.display = 'block';
        }
        review.style.display = 'block';
      }  else { 
        var starContainer = document.getElementById('starContainer');
        if (starContainer) { starContainer.style.display = 'none'; }
        var review = document.getElementById('review');
        if (review) { review.style.display = 'none'; } 
      }
      var starClick = document.getElementById('starClick');
      if (starClick) {
        chrome.storage.sync.get(['sess','password','username'], function(data){ 
          session = data.sess;
          password = data.password;
          username = data.username;
        });
        starClick.addEventListener('click', function() {
          setTimeout(() => {
            var id = this.getAttribute('data-id');
            var r = document.getElementById('rating');
            var vote = r.innerText;
            var xhttp = new XMLHttpRequest();
            xhttp.open("POST", "https://network.tactokens.com/exchange/vote.php", true);
            xhttp.setRequestHeader("Content-type", "application/x-www-form-urlencoded");
            xhttp.setRequestHeader('Cache-Control', 'no-cache');
            xhttp.setRequestHeader('x-data-method', 'form-post');
            xhttp.send("vote="+vote+"&id="+id+"&key="+session+"&password="+password+"&username="+username);
          }, 50);
        });
      }
    });
    lookupReview(page, function(response) {
      if (typeof response === 'object') {
console.log(response);
        var properties = JSON.parse(response);
        if (properties !== false) {
          var site_label_1 = properties.site_label_1;
          document.querySelector('input[name="label1"]').value = site_label_1;
          var site_label_2 = properties.site_label_2;
          document.querySelector('input[name="label2"]').value = site_label_2;
          var site_label_3 = properties.site_label_3;
          document.querySelector('input[name="label3"]').value = site_label_3;
          var site_type = properties.site_type;
          var site_type_list = document.querySelectorAll('.inputButtons button');
          site_type_list.forEach(function(listItem) {
            if(listItem.innerHTML == site_type) { listItem.classList.toggle('active'); } 
          });
          var site_language = properties.site_language;
          var site_language_list = document.querySelectorAll('.locality-dropdown option');
          site_language_list.forEach(function(listItem) {
            if(listItem.value == site_language) { listItem.selected = 'selected'; } 
          });
          var site_category = properties.site_category;
          var site_category_list = document.querySelectorAll('#cat option');
          site_category_list.forEach(function(listItem) {
            if(listItem.value == site_category) { listItem.selected = 'selected'; } 
          });
        } else {
          document.querySelector('input[name="label1"]').value = '';
          document.querySelector('input[name="label2"]').value = '';
          document.querySelector('input[name="label3"]').value = '';
          var checkme = document.querySelector('.active'); 
          if (checkme) { checkme.classList.remove("active"); }
          var site_language_list = document.querySelectorAll('.locality-dropdown option');
          site_language_list.forEach(function(listItem) {
            if(listItem.value == '0') { listItem.selected = 'selected'; } 
          });
          var site_category_list = document.querySelectorAll('#cat option');
          site_category_list.forEach(function(listItem) {
            if(listItem.value == 0) { listItem.selected = 'selected'; } 
          });
        }
      } else {
        document.querySelector('input[name="label1"]').value = '';
        document.querySelector('input[name="label2"]').value = '';
        document.querySelector('input[name="label3"]').value = '';
        var checkme = document.querySelector('.active'); 
        if (checkme) { checkme.classList.remove("active"); }
        var site_language_list = document.querySelectorAll('.locality-dropdown option');
        site_language_list.forEach(function(listItem) {
          if(listItem.value == '0') { listItem.selected = 'selected'; } 
        });
        var site_category_list = document.querySelectorAll('#cat option');
        site_category_list.forEach(function(listItem) {
          if(listItem.value == 0) { listItem.selected = 'selected'; } 
        });
      }
    });
  });