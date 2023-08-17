import math


class ErrorMetrics:

    @staticmethod
    def RMSE(actual, prediction):
        sum = 0

        test_vals = actual["data"].values
        pred_vals = prediction["data"].values

        for i in range(0, len(actual)):
            sum += ((test_vals[i] - pred_vals[i]) ** 2) / len(actual)
        return math.sqrt(sum)

    @staticmethod
    def MAE(actual, prediction):
        sum = 0

        test_vals = actual["data"].values
        pred_vals = prediction["data"].values

        for i in range(0, len(actual)):
            sum += abs(test_vals[i] - pred_vals[i])
        return (1 / len(actual)) * sum

    @staticmethod
    def MSE(actual, prediction):
        sum = 0

        test_vals = actual["data"].values
        pred_vals = prediction["data"].values

        for i in range(0, len(actual)):
            sum += (test_vals[i] - pred_vals[i]) ** 2
        return (1 / len(actual)) * sum
