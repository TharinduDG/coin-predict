# coin-predict

* Bit coin value prediction using coin base api and linear regression
* Used spark-ml for linear regression

## Running
* `sbt run`
* `http://localhost:9000/prices/2018-06-01` to fetch daily prices from coin base
* `http://localhost:9000/rolling/2018-04-01/2018-05-01/10` to get rolling average for coin prices from `2018-04-01` to `2018-05-01` with a window of size `10` 
* `http://localhost:9000/predictions` to get price predictions for the next 15 days from today
