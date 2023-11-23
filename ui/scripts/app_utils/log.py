from datetime import datetime

class bcolors:
    WARNING = "\033[93m"
    FAIL = "\033[91m"
    ENDC = "\033[0m"

class Log:
    @staticmethod
    def info(data):
        date = datetime.today().strftime("%Y/%m/%d %H:%M:%S")
        message = f"[INFO   ][{date}]\t{data}"
        print(message)

    @staticmethod
    def warning(data):
        date = datetime.today().strftime("%Y/%m/%d %H:%M:%S")
        message = f"[WARNING][{date}]\t{data}"
        print(f"{bcolors.WARNING}{message}{bcolors.ENDC}")

    @staticmethod
    def error(data):
        date = datetime.today().strftime("%Y/%m/%d %H:%M:%S")
        message = f"[ERROR  ][{date}]\t{data}"
        print(f"{bcolors.FAIL}{message}{bcolors.ENDC}")
