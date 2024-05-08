//this will create admin user in mongo
//change user and pwd properties how you like
//note that you don't need to create database, server will do it on its own and database name will be serverDb
//you want to only grant admin role in serverDb database just uncomment line under roles and comment old line
//DO NOT CHANGE ROLE AND DB PARAMETER UNLESS YOU KNOW WHAT ARE YOU DOING!!!!!
db = db.getSiblingDB('admin');
db.createUser(
    {
        user: "Jarka",
        pwd: "pwd",
        roles: [{ role: "userAdminAnyDatabase", db: "admin" }] //DO NOT CHANGE ANY PARAMETER!!!
        //roles: [{ role: "userAdmin", db: "serverDb" }] //DO NOT CHANGE ANY PARAMETER!!!
    }
)