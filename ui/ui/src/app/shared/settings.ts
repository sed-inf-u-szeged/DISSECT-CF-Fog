export const SMOOTHING_WINDOW_SIZES = [16, 32, 48, 64, 96, 128];
export const SMOOTHING_POLYNOMIAL_DEGREES = [3, 4, 5, 6, 7, 8];
export const BATCH_SIZES = [32, 64, 96, 128, 160, 192, 224, 256, 512];
export const LOSS_FUNCTIONS = [
    { id: 'mean_squared_error', label: 'Mean squared error' },
    { id: 'mean_absolute_error', label: 'Mean absolute error' },
];
export const OPTIMIZERS = [
    { id: 'Adam', label: 'Adam' },
    { id: 'SGD', label: 'Gradient descent' }
];