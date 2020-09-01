# rest-api-reitit

Example: simply naive Clojure REST API service using [reitit](https://github.com/metosin/reitit) to "manage" users. 

The service API schema and back end Clojure code are using different case letter convention: The API schema is using `camelCase` (populare convention in many programign langugages such as [JavaScript](https://www.w3schools.com/js/js_conventions.asp)) and the backend Clojure code is using `kebab-case` (https://github.com/bbatsov/clojure-style-guide#lisp-case).

## Try it out

Clone this repo into your local hard drive and start the HTTP service:

```clj
> lein repl
(start)
```

Test the REST API endpoints using Swagger UI from http://localhost:3000/api-docs, or from the command line using [httpie](https://httpie.org/):

```bash
# list all users
http GET :3000/v1/users
http GET :3000/v1/users lastName==second

# add new user
http POST :3000/v1/users userName=new-user firstName=new lastName=user

# get specific user
http DELETE :3000/v1/users/2

# update specific user
http POST :3000/v1/users/2 userName=user-2 firstName=new-fname lastName=new-lname

# delete specific user
http DELETE :3000/v1/users/2
```
