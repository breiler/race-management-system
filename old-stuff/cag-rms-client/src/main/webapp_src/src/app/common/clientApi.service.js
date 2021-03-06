(function () {
  "use strict";
  var USER_INFO_KEY = 'cagrms.userinfo';
  var TOKEN_KEY = 'cagrms.token';
  var DEBUG_EVENTS_KEY = 'droid.showevents';
  var X_AUTH_TOKEN = 'X-AuthToken';

  angular.module('cag-rms-client')
    .config(function ($httpProvider) {
      $httpProvider.defaults.headers.common[X_AUTH_TOKEN] = null;
    })
    .service('clientApiService', function ($rootScope, $q, $http, $timeout, md5, APP_CONFIG, localStorageService, notificationService) {
      var service = new Service($rootScope, $q, $http, $timeout, md5, APP_CONFIG, localStorageService, notificationService);
      return service;
    });

  function Service($rootScope, $q, $http, $timeout, md5, APP_CONFIG, localStorageService, notificationService) {
    var self = this;
    var eventBus = new EventBus($rootScope, $timeout, APP_CONFIG, localStorageService, notificationService);

    this.login = function (userId, password) {
      console.debug('Logging in:', userId);
      var loginCredentials = {
        body: {
          userId: userId,
          password: md5.createHash(password)
        }
      };
      return backendRequest('POST', '/login', loginCredentials)
        .then(function (response) {
          var token = response.headers(X_AUTH_TOKEN);
          var userInfo = response.data;
          if (token) {
            localStorageService.set(USER_INFO_KEY, userInfo);
            localStorageService.set(TOKEN_KEY, token);
            return userInfo;
          } else {
            throw 'No token in response';
          }
        });
    };
    this.logout = function () {
      console.debug('Logging out');
      return backendRequest('POST', '/logout')
        .finally(function () {
          localStorageService.set(USER_INFO_KEY, null);
          localStorageService.set(TOKEN_KEY, null);
        });
    };
    this.getCurrentUser = function () {
      return localStorageService.get(USER_INFO_KEY);
    };
    this.getResults = function () {
      return backendRequest('GET','/leaderboard');
    };
    this.getMyRaces = function () {
      var userToSend = {
        userId: self.getCurrentUser().userId,
        displayName: self.getCurrentUser().displayName
      };
      return backendRequest('POST','/myraces', {body: userToSend});
    };
    this.setConnectionListener = function (connectionListener) {
      eventBus.setConnectionListener(connectionListener);
    };
    this.addEventListener = function (eventListener) {
      eventBus.addListener(eventListener);
    };
    this.removeEventListener = function (eventListener) {
      eventBus.removeListener(eventListener);
    };
    this.addUser = function (user) {
      var userToSend = {
        userId: user.userId,
        displayName: user.displayName,
        password: md5.createHash(user.password)
      };
      return backendRequest('POST', '/users', {body: userToSend});
    };
    this.getUserQueue = function () {
      return backendRequest('GET', '/userqueue');
    };
    this.registerForRace = function () {
      var userToSend = {
        userId: self.getCurrentUser().userId,
        displayName: self.getCurrentUser().displayName
      };
      return backendRequest('POST', '/userqueue', {body: userToSend});
    };
    this.unregisterFromRace = function () {
      var userToSend = {
        userId: self.getCurrentUser().userId,
        displayName: self.getCurrentUser().displayName
      };
      return backendRequest('DELETE', '/userqueue', {body: userToSend});
    };
    this.getStatus = function() {
      return backendRequest('GET', '/currentrace');
    };
    this.getLastRace = function() {
      return backendRequest('GET', '/lastrace');
    };

    /**
     * Send a request to backend.
     * @param method the HTTP method
     * @param resourcePath the path to the resource, must start with '/'
     * @param contents object containing data to be sent:
     *
     *     {
     *       body: <optional javascript object, which will be sent as JSON>,
     *       headers: { <optional set of headers>
     *        <name>:<value>
     *        :
     *       },
     *       params: { <optional set of query parameters>
     *        <name>:<value>,
     *        :
     *
     *       }
     *     }
     */
    function backendRequest(method, resourcePath, contents) {
      contents = contents || {headers: {}};
      contents.headers = contents.headers || {};
      contents.params = contents.params || {};
      contents.headers[X_AUTH_TOKEN] = localStorageService.get(TOKEN_KEY);
      contents.headers['Content-Type'] = 'application/json';
      contents.params.reqver = new Date().getTime();
      return $http({
        method: method,
        url: APP_CONFIG.clientApi + resourcePath,
        params: contents.params,
        headers: contents.headers,
        data: contents.body
      });
    }
  }

  function EventBus($rootScope, $timeout, APP_CONFIG, localStorageService, notificationService) {
    var state = 'NOT_CONNECTED';
    var listeners = [];
    var connectionListener;
    checkConnection();

    this.setConnectionListener = function (l) {
      connectionListener = l;
    };

    this.addListener = function (listener) {
      listeners.push(listener);
    };

    this.removeListener = function (listener) {
      _.remove(listeners, function(l){
        return l === listener;
      });
    };

    function checkConnection() {
      if (state === 'NOT_CONNECTED') {
        connect();
      }

      $timeout(checkConnection, 3000);
    }

    function connect() {
      state = 'CONNECTING';
      var wsUri = APP_CONFIG.clientApi + '/eventchannel';
      notifyConnectionListener();

      var eventBusWS = new SockJS(wsUri);
      eventBusWS.onopen = function () {
        state = 'CONNECTED';
        notifyConnectionListener();
      };
      eventBusWS.onmessage = function (event) {
        var data = event.data;
        state = 'CONNECTED';
        console.debug('Received from WS: ', data, 'state:', state);
        if (localStorageService.get(DEBUG_EVENTS_KEY) === true) {
          notificationService.showInfoMessage(data);
        }

        _.forEach(listeners, function (l) {
          l(JSON.parse(data));
        });
        notifyConnectionListener();
      };
      eventBusWS.onclose = function () {
        state = 'NOT_CONNECTED';
        console.debug('WS connection closed. State:', state);
        notifyConnectionListener();
      };

      function notifyConnectionListener() {
        console.debug('WS to:', wsUri, 'State:', state);

        if (connectionListener) {
          $rootScope.$apply(function () {
            connectionListener(state);
          });
        }
      }
    }

  }
}());