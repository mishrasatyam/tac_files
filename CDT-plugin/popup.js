/* definitions */
  var session, password, username, cats, page, id, wallet; 
  var sites = [];
/* end definitions */


/* fadeout effect */
  function fadeOutEffect() {
    var fadeTarget = document.getElementById("error");
    var fadeEffect = setInterval(function () {
      if (!fadeTarget.style.opacity) {
        fadeTarget.style.opacity = 1;
      }
      if (fadeTarget.style.opacity > 0) {
        fadeTarget.style.opacity -= 0.1;
      } else {
        clearInterval(fadeEffect);
        fadeTarget.remove();
      }
    }, 90);
  }
/* end fadeout effect */


/* loggedin session variables */
  function sessionCheckup() {
    chrome.storage.sync.get(['sess','password','username', 'wallet'], function(data){ 
      session = data.sess;
      password1 = data.password;
      password = encodeURIComponent(password1);
      username = data.username;
      wallet = data.wallet;
      if (wallet == '') {
        wallet = 0; 
      }
    });
  }
/* end loggedin session check */


/* resolution check */
  function resolutionCheck () { 
    chrome.windows.getCurrent( function( current ) {
      var node = document.querySelector('body');
      if (current.width < 800) { 
        node.classList.add('small');
      } else { 
        node.classList.remove('small');
      }
    });
  }
  resolutionCheck();
  chrome.windows.onBoundsChanged.addListener(resolutionCheck);
/* end resolution check */


/* logged in session checkup */
  function sessionCheck() {
    sessionCheckup();
    var div1 = document.getElementById('login');
    var div2 = document.getElementById('signup');
    var div3 = document.getElementById('reviewForm');
    var div4 = document.getElementById('reset');
    var div5 = document.getElementById('resetKeyForm');
    setTimeout(function () {
      if (typeof session === "undefined") {
        div1.style.display = 'block';
        div2.style.display = 'none';
        div3.style.display = 'none';
        div4.style.display = 'none';
        div5.style.display = 'none';
        var switch1 = document.getElementById('switch1');
        switch1.addEventListener('click', function() {
          div1.style.display = 'block';
          div2.style.display = 'none';
          div3.style.display = 'none';
          div4.style.display = 'none';
          div5.style.display = 'none';
        });
        var switch2 = document.getElementById('switch2');
        switch2.addEventListener('click', function() {
          div1.style.display = 'none';
          div2.style.display = 'block';
          div3.style.display = 'none';
          div4.style.display = 'none';
          div5.style.display = 'none';
        });
        var switch3 = document.getElementById('switch3');
        switch3.addEventListener('click', function() {
          div1.style.display = 'none';
          div2.style.display = 'block';
          div3.style.display = 'none';
          div4.style.display = 'none';
          div5.style.display = 'none';
        });
        var switch4 = document.getElementById('switch4');
        switch4.addEventListener('click', function() {
          div1.style.display = 'none';
          div2.style.display = 'block';
          div3.style.display = 'none';
          div4.style.display = 'none';
          div5.style.display = 'none';
        });
        var reset1 = document.getElementById('reset1');
        reset1.addEventListener('click', function() {
          div1.style.display = 'none';
          div2.style.display = 'none';
          div3.style.display = 'none';
          div4.style.display = 'block';
          div5.style.display = 'none';
        });
        var reset2 = document.getElementById('reset2');
        reset2.addEventListener('click', function() {
          div1.style.display = 'block';
          div2.style.display = 'none';
          div3.style.display = 'none';
          div4.style.display = 'none';
          div5.style.display = 'none';
        });
        var reset3 = document.getElementById('reset3');
        reset3.addEventListener('click', function() {
          div1.style.display = 'none';
          div2.style.display = 'none';
          div3.style.display = 'none';
          div4.style.display = 'block';
          div5.style.display = 'none';
        });
        var reset4 = document.getElementById('reset4');
        reset4.addEventListener('click', function() {
          div1.style.display = 'block';
          div2.style.display = 'none';
          div3.style.display = 'none';
          div4.style.display = 'none';
          div5.style.display = 'none';
        });
        var resetKey = document.getElementById('resetKey');
        resetKey.addEventListener('click', function() {
          div1.style.display = 'none';
          div2.style.display = 'none';
          div3.style.display = 'none';
          div4.style.display = 'none';
          div5.style.display = 'block';
        });
      } else {
          div1.style.display = 'none';
          div2.style.display = 'none';
          div3.style.display = 'block';
          div4.style.display = 'none';
      }
    }, 100);
  }
  sessionCheck();
/* end logged in session checkup */


/* logged in session lookup */
  function sessionLookup () {
    sessionCheckup();
    setTimeout(function() {
      var xhttp = new XMLHttpRequest();
      xhttp.open('POST', 'https://network.tactokens.com/exchange/session_checkup.php');
      xhttp.setRequestHeader("Content-type", "application/x-www-form-urlencoded");
      xhttp.setRequestHeader('x-data-method', 'form-post');
      xhttp.setRequestHeader('Cache-Control', 'no-cache');
      xhttp.send("key="+session+"&password="+password+"&username="+username);
      xhttp.onreadystatechange = function () {
        if (xhttp.readyState == 4 && xhttp.status == "200") {
          var checkOnline = xhttp.responseText;
          if (checkOnline == 'false') {
            chrome.storage.sync.remove(['sess', 'password', 'username']);
            username = null; session = null; password = null;
            sessionCheck(); 
          } else if (checkOnline == 'Error: This ip is already in use.') {
            chrome.storage.sync.remove(['sess', 'password', 'username']);
            username = null; session = null; password = null;
            sessionCheck();
            var div = document.getElementById('login');
            var error = document.createElement("div");
            error.innerHTML = checkOnline;
            error.id = "error";
            setTimeout( function(){
              div.prepend(error);
            }, 1000);
          } else {
            sessionCheck(); 
          }
        }
      }
    }, 50);
  }
/* end logged in session checkup*/


/* perform a complete checkup */
  sessionLookup();
  setInterval(function() {
    sessionLookup();    
  }, 120000);
/* end perform a complete checkup */


/* lookup 10 sites */
  function loadJSON(data) {
    chrome.tabs.query({active: true,currentWindow: true}, function(tabs) {
      getCats();
      current_url = atab.url;
      setTimeout( function() {
        var xhttp = new XMLHttpRequest();
        xhttp.open('POST', 'https://network.tactokens.com/exchange/', true);
        xhttp.setRequestHeader("Content-type", "application/x-www-form-urlencoded");
        xhttp.setRequestHeader("x-data-method", "form-post");
        xhttp.setRequestHeader('Cache-Control', 'no-cache');
        xhttp.onreadystatechange = function () {
          if (xhttp.readyState == 4 && xhttp.status == "200") {
            data(JSON.parse(xhttp.responseText));
          }
        }
        xhttp.send("key="+session+"&password="+password+"&username="+username+"&cats="+cats+"&url="+current_url);
      }, 300);
    });
  }
/* end lookup 10 sites */


/* report button click */
  document.querySelector('.nav a:nth-child(4)').addEventListener('click', function() {
    var report = document.getElementById('report');
    if ( report.style.display == 'none') { report.style.display = 'block'; } else { report.style.display = 'none'; }
  });
  document.querySelector('.backButton').addEventListener('click', function() {
    var report = document.getElementById('report');
    if ( report.style.display == 'none') { report.style.display = 'block'; } else { report.style.display = 'none'; }
  });
/* end report button click */


/* file a report */
  document.querySelector('.reportButton').addEventListener('click', function() {
    var reasonContainer = document.querySelector('.reportReason');
    var reason = document.querySelector('.reportReason').innerText;
    if (reason == '') {
      reasonContainer.focus(); setTimeout( function() { document.activeElement.blur(); }, 500); return;
    } else { 
      var xhttp = new XMLHttpRequest();
      xhttp.open('POST', 'https://network.tactokens.com/exchange/report.php', true);
      xhttp.setRequestHeader("Content-type", "application/x-www-form-urlencoded");
      xhttp.setRequestHeader("x-data-method", "form-post");
      xhttp.setRequestHeader('Cache-Control', 'no-cache');
      xhttp.onreadystatechange = function (reponse) {
        if (xhttp.readyState == 4 && xhttp.status == "200") {
          var data = xhttp.responseText;
          var firstChars = data.substring(0, 5);
          if (firstChars == 'Error') {
            var div1 = document.querySelector('h1');
            var error = document.createElement("div");
            error.innerHTML = data; 
            error.id = "error";
            div1.parentNode.insertBefore(error,div1.nextSibling);
            div1.nextSibling.style.marginTop = '0px';
            setTimeout(() => {
              error.addEventListener('click', fadeOutEffect);
              error.click();
            }, 3000);
          } else { 
            report.style.display = 'none';
          }
        }
      }
      xhttp.send("key="+session+"&password="+password+"&username="+username+"&site="+page+"&report="+reason);
    }
  });
/* end file a report */


/* language dropdown list */
  function getLang() {
    var dropdown = document.getElementById('lang-dropdown');
    dropdown.length = 0;
    var defaultOption = document.createElement('option');
    defaultOption.text = 'SELECT WEBPAGE LANGUAGE';
    defaultOption.value = '0';
    var firstOption = document.createElement('option');
    firstOption.value = 'en-us';
    firstOption.text = 'English (United States)';
    dropdown.add(defaultOption);
    dropdown.add(firstOption);
    dropdown.selectedIndex = 0;
    var url = 'https://gist.githubusercontent.com/Josantonius/b455e315bc7f790d14b136d61d9ae469/raw/009009121fdc29f908cd3249b7729e7751057a65/languageCodes.json';
    const request = new XMLHttpRequest();
    request.open('GET', url, true);
    request.onload = function() {
      if (request.status === 200) {
        var jdata = JSON.parse(request.responseText);
        for (var key in jdata.data) {
          var opt = document.createElement('option');
          opt.value = key;
          opt.innerHTML = jdata.data[key];
          dropdown.appendChild(opt);
        }
      }   
    }
    request.send();
  }
  getLang();
/* end langauge dropdown list */


/* lookup star ratings for active tab */
  function loadJSON2(page, callback) {
    chrome.tabs.query({active: true,currentWindow: true}, function(tabs) {
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
          var properties1 = JSON.parse(xhttp.responseText);
          if (properties1[0].rating !== undefined) {
            var className="className";
            rateSystem(className, properties1, function(rating, ratingTargetElement){ ratingTargetElement.parentElement.parentElement.parentElement.parentElement.parentElement.getElementsByClassName("className")[1].innerHTML = rating; });
            document.getElementById("ratingNum").innerHTML = properties1[0].rating;
            var starContainer = document.getElementById('starContainer');
            starContainer.style.display = 'block';
            var review = document.getElementById('review');
            review.style.display = 'block';
          } else { 
            var starContainer = document.getElementById('starContainer');
            starContainer.style.display = 'none';
            var review = document.getElementById('review');
            review.style.display = 'none';
          }
          callback(xhttp.responseText);
        }
      };  
    });
  }
/* end lookup star ratings for active tab */


/* toggle star ratings display */
  function starToggle() {
    chrome.tabs.query({active: true,currentWindow: true}, function(tabs) {
      atab = tabs[0];
      page = atab.url;
      loadJSON2(page, function(response) {
        var elem = document.querySelector('#star > *');
        var elem2 = document.querySelector('#star');
        var mySpan = document.createElement("div");
        var mySpan2 = document.createElement("div");
        elem.parentNode.removeChild(elem);
        elem2.append(mySpan);
        mySpan.append(mySpan2);
        mySpan.classList.toggle('starRatingContainer');
        mySpan2.classList.toggle('className');
        mySpan2.id = 'starClick';
        var properties1 = JSON.parse(response);
        if (properties1[0].rating !== undefined) {
          var className="className";
          rateSystem(className, properties1, function(rating, ratingTargetElement){ ratingTargetElement.parentElement.parentElement.parentElement.parentElement.parentElement.parentElement.getElementsByClassName("className")[1].innerHTML = rating; });
          document.getElementById("ratingNum").innerHTML = properties1[0].rating;
          var starContainer = document.getElementById('starContainer');
          starContainer.style.display = 'block';
          document.querySelector('.nav a:nth-child(4)').style.display = 'block';
          var review = document.getElementById('review');
          if (properties1[0].rated == 1) { 
            var remove = document.querySelector('.rate');
            remove.style.display = 'none';
          } else {
            var remove = document.querySelector('.rate');
            remove.style.display = 'block';
          }
          review.style.display = 'block';
          addStar();
        }  else { 
          var starContainer = document.getElementById('starContainer');
          starContainer.style.display = 'none';
          var review = document.getElementById('review');
          review.style.display = 'none';
          document.querySelector('.nav a:nth-child(4)').style.display = 'none';
        }
      });
    });
  }
  starToggle();
/* end toggle star ratings display */


/* add star rating */
  function addStar() {
    var starClick = document.getElementById('starClick');
    starClick.addEventListener('click', function() {
      var id = this.getAttribute('data-id');
      setTimeout(() => {
        var vote = this.getAttribute('data-rating');
        var xhttp = new XMLHttpRequest();
        xhttp.open("POST", "https://network.tactokens.com/exchange/vote.php", true);
        xhttp.setRequestHeader("Content-type", "application/x-www-form-urlencoded");
        xhttp.setRequestHeader('x-data-method', 'form-post');
        xhttp.setRequestHeader('Cache-Control', 'no-cache');
        xhttp.send("vote="+vote+"&id="+id+"&key="+session+"&password="+password+"&username="+username);
      }, 10);
    });
  }
/* end add star rating */


/* select 1 random site from lookup and formward to active tab */
  var nextpageButton = document.getElementById('nextpage');
  nextpageButton.addEventListener('click', function() {
    loadJSON(function(json) {
      var limit = json.length;
      var i = ((Math.floor(Math.random()*limit)+1) - 1);
      if (limit != 1 && i != 0 && id != 0) {
        while (i == id) {
          i = ((Math.floor(Math.random()*limit)+1) - 1);
        }
      }
      page = (json[i].sites);
      var select_element = document.getElementById('cat');
      select_element.value = '0';
      chrome.tabs.query({currentWindow: true, active: true}, function (tab) {
        id = i;
        chrome.tabs.update(tab.id, {url: page});
        setTimeout(function() {
          starToggle();
          getReview();
        }, 1500);
      });
    });
  });
/* end select 1 random site from lookup and formward to active tab */


/* user profile */
  document.querySelector('.nav a:nth-child(1)').addEventListener('click', function() {
    chrome.tabs.query({currentWindow: true, active: true}, function (tab) {
      chrome.tabs.update(tab.id, {url: 'https://network.tactokens.com/profile/'+username+'/'});
    });
  });
/* end user profile */


/* advertising */
  document.querySelector('.nav a:nth-child(3)').addEventListener('click', function() {
    chrome.tabs.query({currentWindow: true, active: true}, function (tab) {
      chrome.tabs.update(tab.id, {url: 'https://network.tactokens.com/advertising.php'});
    });
  });
/* end advertising */


/* open settings page button */
  document.querySelector('.nav a:nth-child(2)').addEventListener('click', function() {
    if (chrome.runtime.openOptionsPage) {
      chrome.runtime.openOptionsPage();
    } else {
      window.location.replace(chrome.runtime.getURL('options.html'));
    }
  });
/* open settings page button */


/* webpage type form button selectors */
  document.querySelectorAll('#inputButtons .btn').forEach(item => {
    item.addEventListener('click', event => {
      document.querySelectorAll('#inputButtons .btn').forEach(items => {
        items.classList.remove('active');
      });
      item.classList.add('active');
    });
  });
/* end webpage type form button selectors */


/* get discovery categories from storage */
  function getCats () { 
    if (typeof cats == 'undefined') {
      chrome.storage.sync.get(null,function(items) {
        var allKeys = Object.keys(items);
        allKeys.forEach(function(key) {
          if (key.substring(0, 4) == 'site') { 
            sites.push(key.slice(4));
          }
        });
        cats = sites.join(',');
        if (cats == '') { 
          cats = 0;
        }
      });
    }
  }
/* end get discovery categories from storage */


/* textarea character check */
  var textCheck = document.getElementById('review');
  textCheck.addEventListener("keyup", function(event){
    str = textCheck.value;
      n = str.replace(/[^a-zA-Z0-9 ".,!?#@$%()\r\n|\r|\n\']/g, '');
/*    n = str.replace(/[^a-zA-Z0-9 ".,!?#@$%()\r\n|\r|\n\']/g, ''); */
    textCheck.value = n;
  });
/* end textarea character check */


/* lookup user review for current page */
  function lookupReview(page, callback) {
    chrome.tabs.query({active: true,currentWindow: true}, function(tabs) {
      chrome.storage.sync.get(['sess','password','username'], function(data){ 
        session = data.sess;
        password1 = data.password;
        password = encodeURIComponent(password1);
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
/* end lookup user review for current page */


/* get review details for current page */
  function getReview() {
    chrome.tabs.query({active: true,currentWindow: true}, function(tabs) {
      atab = tabs[0];
      page = atab.url;
      lookupReview(page, function(response) {
        if (response.charAt(0) == '[' || response.charAt(0) == '{') {
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
              if(listItem.innerHTML == site_type) { listItem.classList.add('active'); } else { listItem.classList.remove('active'); }
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
            document.getElementById('review').value = properties.review;
            document.querySelector('.reportReason').innerText = '';
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
            document.getElementById('review').value = '';
            document.querySelector('.reportReason').innerText = '';
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
          document.getElementById('review').value = '';
        }
      });
    })
  }
  getReview();
/* get review details for current page */


/* login */
  var loginButton = document.getElementById('loginButton');
  loginButton.addEventListener('click', function(event) {
    event.preventDefault();
    var dheight = document.documentElement.scrollHeight;
    var dwidth = document.documentElement.scrollWidth;
    var node = document.querySelector('body');
    var loading = document.createElement('iframe');
    loading.src = 'https://network.tactokens.com/exchange/loader/preloader.html';
    loading.style.cssText = "position: absolute; top: 0; left: 0; border:0; height: "+dheight+"px; width: "+dwidth+"px";
    node.append(loading);

    var loginName = document.getElementById('loginName').value;
    var loginPass1 = document.getElementById('loginPassword').value;
    var loginPass = encodeURIComponent(loginPass1);
    var xhttp = new XMLHttpRequest();
    xhttp.open("POST", "https://network.tactokens.com/exchange/login.php", true);
    xhttp.setRequestHeader("Content-type", "application/x-www-form-urlencoded");
    xhttp.setRequestHeader('x-data-method', 'form-post');
    xhttp.setRequestHeader('Cache-Control', 'no-cache');
    xhttp.onreadystatechange = function() {
      if (this.readyState == 4 && this.status == 200) {
        setTimeout( function(){ loading.remove(); }, 1000);
        var data = xhttp.responseText;
        var firstChars = data.substring(0, 5);
        if (firstChars == 'Error') {
          var div = document.getElementById('login');
          var error = document.createElement("div");
          error.innerHTML = data;
          error.id = "error";
          setTimeout( function(){
            div.prepend(error);
          }, 1000);
          setTimeout(() => {
            error.addEventListener('click', fadeOutEffect);
            error.click();
          }, 3000);
        } else { 
          var [apikey, password, username] = data.split(',');
          chrome.storage.sync.set({'sess': apikey, 'password': password, 'username': username}, function() {});
          location.reload();
        }
      }
    }
    xhttp.send('username=' +loginName+'&password='+loginPass);  
  });
/* end login */


/* reset */
  var resetButton = document.getElementById('resetButton');
  resetButton.addEventListener('click', function(event) {
    event.preventDefault();
    var resetName = document.getElementById('resetName').value;
    var resetEmail = document.getElementById('resetEmail').value;
    var dheight = document.documentElement.scrollHeight;
    var dwidth = document.documentElement.scrollWidth;
    var node = document.querySelector('body');
    var loading = document.createElement('iframe');
    loading.src = 'https://network.tactokens.com/exchange/loader/preloader.html';
    loading.style.cssText = "position: absolute; top: 0; left: 0; height: "+dheight+"px; width: "+dwidth+"px";
    node.append(loading);
    var xhttp = new XMLHttpRequest();
    xhttp.open("POST", "https://network.tactokens.com/exchange/reset.php", true);
    xhttp.setRequestHeader("Content-type", "application/x-www-form-urlencoded");
    xhttp.setRequestHeader('x-data-method', 'form-post');
    xhttp.setRequestHeader('Cache-Control', 'no-cache');
    xhttp.onreadystatechange = function() {
      if (this.readyState == 4 && this.status == 200) {
        setTimeout( function(){ loading.remove(); }, 1000);
        var data = xhttp.responseText;
        var firstChars = data.substring(0, 5);
        if (firstChars == 'Error') {
          var div = document.getElementById('reset');
          var error = document.createElement("div");
          error.innerHTML = data;
          error.id = "error";
          setTimeout( function(){
            div.prepend(error);
          }, 1000);
          setTimeout(() => {
            error.addEventListener('click', fadeOutEffect);
            error.click();
          }, 3000);
        }
      }
    }
    xhttp.send('username=' +resetName+'&email='+resetEmail);  
  });
/* end reset */


/* reset Key */
  var resetKeyButton = document.getElementById('resetKeyButton');
  resetKeyButton.addEventListener('click', function(event) {
    event.preventDefault();
    var resetKeyName = document.getElementById('resetKeyName').value;
    var resetKeyEmail = document.getElementById('resetKeyEmail').value;
    var password1 = document.getElementById('inputKeyPassword').value;
    var password3 = document.getElementById('inputKeyPassword2').value;
    var password = encodeURIComponent(password1);
    var password2 = encodeURIComponent(password3);
    var resetKey = document.getElementById('tresetKey').value;
    var dheight = document.documentElement.scrollHeight;
    var dwidth = document.documentElement.scrollWidth;
    var node = document.querySelector('body');
    var loading = document.createElement('iframe');
    loading.src = 'https://network.tactokens.com/exchange/loader/preloader.html';
    loading.style.cssText = "position: absolute; top: 0; left: 0; height: "+dheight+"px; width: "+dwidth+"px";
    node.append(loading);
    var xhttp = new XMLHttpRequest();
    xhttp.open("POST", "https://network.tactokens.com/exchange/reset.php", true);
    xhttp.setRequestHeader("Content-type", "application/x-www-form-urlencoded");
    xhttp.setRequestHeader('x-data-method', 'form-post');
    xhttp.setRequestHeader('Cache-Control', 'no-cache');
    xhttp.onreadystatechange = function() {
      if (this.readyState == 4 && this.status == 200) {
        setTimeout( function(){ loading.remove(); }, 1000);
        var data = xhttp.responseText;
        var firstChars = data.substring(0, 5);
        if (firstChars == 'Error') {
          var div = document.getElementById('resetKeyForm');
          var error = document.createElement("div");
          error.innerHTML = data;
          error.id = "error";
          setTimeout( function(){
            div.prepend(error);
          }, 1000);
          setTimeout(() => {
            error.addEventListener('click', fadeOutEffect);
            error.click();
          }, 3000);
        } else if (firstChars == 'Done!') {
          sessionCheck();
        }
      }
    }
    xhttp.send('username=' +resetKeyName+'&email='+resetKeyEmail+'&resetKey='+resetKey+'&password='+password+'&password2='+password2);  
  });
/* end reset Key */


/* signup */
  var signupButton = document.getElementById('signupButton');
  signupButton.addEventListener('click', function() {
    var inputName = document.getElementById('inputName');
    var inputEmail = document.getElementById('inputEmail');
    var inputPassword = document.getElementById('inputPassword');
    var inputPassword2 = document.getElementById('inputPassword2');
    var inputTAC = document.getElementById('inputTAC');
    var username = inputName.value;
    var email = inputEmail.value;
    var password1 = inputPassword.value;
    var password = encodeURIComponent(password1);
    var password3 = inputPassword2.value;
    var password2 = encodeURIComponent(password3);
    var dheight = document.documentElement.scrollHeight;
    var dwidth = document.documentElement.scrollWidth;
    var node = document.querySelector('body');
    var loading = document.createElement('iframe');
    loading.src = 'https://network.tactokens.com/exchange/loader/preloader.html';
    loading.style.cssText = "position: absolute; top: 0; left: 0; height: "+dheight+"px; width: "+dwidth+"px";
    node.append(loading);

    var xhttp = new XMLHttpRequest();
    xhttp.open("POST", "https://network.tactokens.com/exchange/signup.php", true);
    xhttp.setRequestHeader("Content-type", "application/x-www-form-urlencoded");
    xhttp.setRequestHeader('x-data-method', 'form-post');
    xhttp.setRequestHeader('Cache-Control', 'no-cache');
    xhttp.send("username="+username+"&email="+email+"&password="+password+"&password2="+password2);
    xhttp.onreadystatechange = function() {
      if (this.readyState == 4 && this.status == 200) {
        setTimeout( function(){ loading.remove(); }, 1000);
        var data = xhttp.responseText;
        var firstChars = data.substring(0, 5);
        var firstChars2 = data.substring(0, 9);
        var firstChars3 = data.substring(0, 10);
        if (firstChars == 'Error' || firstChars2 == 'trueError' || firstChars3 == 'falseError') {
          var div = document.getElementById('signup');
          var error = document.createElement("div");
          error.innerHTML = data;
          error.id = "error";
          setTimeout( function(){
            div.prepend(error);
          }, 1000);
          setTimeout(() => {
            error.addEventListener('click', fadeOutEffect);
            error.click();
          }, 3000);
        } else { 
          var [apikey, password, username] = data.split(',');
          chrome.storage.sync.set({'sess': apikey, 'password': password, 'username': username}, function() {});
          location.reload();
        }
      }
    }
  });
/* end signup */


/* add review */
  function addReview(userLike, userDislike) {
    var atab = '';
    var current_url = '';
    var current_title = '';
    var like = document.getElementById('tu');
    var div1 = document.querySelector('h1');
    var select_element = document.getElementById('cat');
    var cat = select_element.value;
    var lang_element = document.getElementById('lang-dropdown');
    var lang = lang_element.value;
    var tabs_element = document.getElementById('inputButtons');
    var tabs = document.querySelectorAll('#inputButtons .active');
    if (tabs.length > 0) { var type = tabs[0].innerText; }
    var labs1_element = document.querySelector('#reviewForm input:nth-child(3)');
    var labs2_element = document.querySelector('#reviewForm input:nth-child(4)');
    var labs3_element = document.querySelector('#reviewForm input:nth-child(5)');
    var labs1 = document.querySelector('#reviewForm input:nth-child(3)').value;
    var labs2 = document.querySelector('#reviewForm input:nth-child(4)').value;
    var labs3 = document.querySelector('#reviewForm input:nth-child(5)').value;
    var textReview_element = document.getElementById('review');


    if (cat < 1) { select_element.focus(); setTimeout( function() { document.activeElement.blur(); }, 500); return;  }
    if (lang < 1) { lang_element.focus(); setTimeout( function() { document.activeElement.blur(); }, 500); return; }
    if (tabs.length < 1) { 
      tabs_element.tabIndex = 14; 
      tabs_element.focus(); 
      setTimeout( function() { 
        document.activeElement.blur(); 
      }, 500); 
      return; 
    }
    var siteType = tabs[0].outerText;
    if (labs1 == '' || labs2 == '' || labs3 == '') {
      if (labs1 == '') { labs1_element.focus(); setTimeout( function() { document.activeElement.blur(); }, 500); return; }
      else if (labs2 == '') { labs2_element.focus(); setTimeout( function() { document.activeElement.blur(); return; }, 500); return; }
      else if (labs3 == '') { labs3_element.focus(); setTimeout( function() { document.activeElement.blur(); return; }, 500); return; }
    }
    if (textReview_element.style.display != 'none') {
      var textReview = textReview_element.value; 
      if (textReview.trim() == '') { 
        textReview_element.focus(); 
        setTimeout( function() { 
          document.activeElement.blur(); 
        }, 500); 
        return; 
      } 
    } else { 
      var textReview = '';
    }
    chrome.tabs.query({active: true,currentWindow: true}, function(tabs) {
      atab = tabs[0];
      current_url = atab.url;
      current_title = atab.title;
      var typ = tabs[0].innerText;
      var xhttp = new XMLHttpRequest();
      xhttp.open("POST", "https://network.tactokens.com/exchange/add.php", true);
      xhttp.setRequestHeader("Content-type", "application/x-www-form-urlencoded");
      xhttp.setRequestHeader('x-data-method', 'form-post');
      xhttp.setRequestHeader('Cache-Control', 'no-cache');
      xhttp.send("key="+session+"&password="+password+"&username="+username+"&sites="+current_url+"&title="+current_title+"&vote_up="+userLike+"&vote_down="+userDislike+"&cat="+cat+"&type="+type+"&tag1="+labs1+"&tag2="+labs2+"&tag3="+labs3+"&lang="+lang+"&text_review="+textReview+"&wallet="+wallet);
      xhttp.onreadystatechange = function() {
        if(xhttp.readyState == 4 && xhttp.status == 200) {
          var data = xhttp.responseText;
          var firstChars = data.substring(0, 5);
          if (firstChars == 'Error') {
            var error = document.createElement("div");
            error.innerHTML = data; 
            error.id = "error";
            div1.parentNode.insertBefore(error,div1.nextSibling);
            div1.nextSibling.style.marginTop = '0px';
            setTimeout(() => {
              error.addEventListener('click', fadeOutEffect);
              error.click();
            }, 3000);
            return false;
          }
          loadJSON(function(json) {
            select_element.value = '0';
            var limit = json.length;
            var i = ((Math.floor(Math.random()*limit)+1) - 1);
            while (i == id) {
              i = ((Math.floor(Math.random()*limit)+1) - 1);
            }
            page = (json[i].sites);
            chrome.tabs.query({currentWindow: true, active: true}, function (tab) {
              id = i;
              chrome.tabs.update(tab.id, {url: page});
              setTimeout(function() {
                starToggle();
                getReview();
              }, 1000);
            });
          });
        }
      }
    });
  }
/* end add review */


/* like page */
  var like = document.getElementById('tu');
  like.addEventListener('click', function() {
    var userLike = '1';
    var userDislike = '0';
    addReview(userLike, userDislike);
  });
/* end like page */


/* dislike page */
  var like = document.getElementById('td');
  like.addEventListener('click', function() {
    var userLike = '0';
    var userDislike = '1';
    addReview(userLike, userDislike);
  });
/* end dislike page */