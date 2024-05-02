//this will create admin user in mongo
//change user and pwd properties how you like
//note that you don't need to create database, server will do it on its own and database name will be serverDb
//you want to only grant admin role serverDb database just uncomment line under roles and comment old line
db.createUser(
    {
        user: "admin",
        pwd: "pwd",
        roles: [{ role: "userAdminAnyDatabase", db: "admin" }]
        //roles: [{ role: "userAdmin", db: "yourDatabaseName" }]
    }
)