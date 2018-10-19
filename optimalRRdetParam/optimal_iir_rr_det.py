import numpy as np
import pylab as pl
import scipy.optimize as optimize

from scipy.signal import butter, lfilter

global rr
global order
global fs

order = 4
fs = 250

def bandpass_impulse(center, width):
    nyq = 0.5 * fs
    low = (center - (width/2)) / nyq
    high = (center + (width/2)) / nyq
    if low < 0:
        low = 0
    if high < 0:
        high = 0
    b, a = butter(order, [low, high], btype='band')
    h = np.zeros(len(rr))
    h[1] = 1
    h = lfilter(b,a,h) * 1000
    return h

def abserror(params):
    center, width = params
    h = bandpass_impulse(center,width)
    e = 0
    h = h[::-1]
    for i in range(len(rr)):
        e = e + h[i] * rr[i]
    e = 1/(np.fabs(e)**2)
    print(center,width,e)
    return e

data = np.loadtxt('ecg.tsv');
#
rr = data[180:220,2]
rr = rr - np.mean(rr)
pl.subplot(211)
pl.plot(rr)

initial_guess = [20, 5]
bds = [ [1,50], [0.1,50]]
m = 'SLSQP'
result = optimize.minimize(abserror, initial_guess, bounds=bds, method=m)
print(result)

h = bandpass_impulse(result.x[0], result.x[1])
pl.subplot(212)
pl.plot(h)
pl.show()
