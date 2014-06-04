Application.Services.service('sessionService', ['$q', 'alerts', '$rootScope', function ($q, alerts, $rootScope) {

	this.authorizeUser = function (data) {
		var deferred = $q.defer();
		var message = {
			"name" : data.name,
			"email" : data.email,
			"account" : this.getAccount().id
		};

        var request = gapi.client.booking.person.authorizePerson(message).execute(function(resp) {
            if (!resp.code) {
                deferred.resolve(resp);
            } else {
                deferred.reject('error');
            }
        });
        return deferred.promise;
	};

    this.setAccount = function (account) {
        this.account = account;
    };
    this.getAccount = function () {
        return this.account;
    };

    this.createUserDetails = function (data) {
		this.authorizeUser(data)
		.then(function (data) {
            alerts.clear();
            this.userName = data.username;
            this.userRole = data.userType;
            this.email = data.email;
            $rootScope.$broadcast('EventLoaded');
        }, function (reason) {
            //$location.path('/home');
            //this.destroy();
            alerts.setAlert({
                'alertMessage': "create user Sorry you could not be authenticated " + reason,
                'alertType': 'alert-danger'});
        });
    };
    this.destroy = function () {
        this.userName = null;
        this.userRole = null;
    };
    return this;
}]);

